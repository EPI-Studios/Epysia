package fr.epistudio.epysia.render.mesh;

public final class VertexCacheOptimizer {

    public static final int SIMULATED_CACHE_SIZE = 32;

    private static final int SCORE_CACHE_SIZE = 32;
    private static final float CACHE_DECAY_POWER = 1.5f;
    private static final float LAST_TRIANGLE_SCORE = 0.75f;
    private static final float VALENCE_BOOST_SCALE = 2.0f;
    private static final float VALENCE_BOOST_POWER = 0.5f;
    private static final int NOT_IN_CACHE = -1;

    private VertexCacheOptimizer() {
    }

    public static float averageCacheMissRatio(int[] indices, int cacheSize) {
        if (indices.length < 3) {
            return 0.0f;
        }
        int[] cache = new int[cacheSize];
        java.util.Arrays.fill(cache, -1);
        int cursor = 0;
        int misses = 0;
        for (int index : indices) {
            if (contains(cache, index)) {
                continue;
            }
            cache[cursor] = index;
            cursor = (cursor + 1) % cacheSize;
            misses++;
        }
        return (float) misses / (indices.length / 3);
    }

    private static boolean contains(int[] cache, int value) {
        for (int entry : cache) {
            if (entry == value) {
                return true;
            }
        }
        return false;
    }

    public static MeshData optimize(MeshData mesh) {
        int[] indices = mesh.indices().clone();
        for (Submesh submesh : mesh.submeshes()) {
            optimizeRange(indices, submesh, mesh.vertexCount());
        }
        int[] remap = fetchOrderPermutation(indices, mesh.vertexCount());
        return new MeshData(
                permuteFloats(mesh.positions(), remap, MeshData.POSITION_COMPONENTS),
                permuteFloats(mesh.normals(), remap, MeshData.NORMAL_COMPONENTS),
                permuteFloats(mesh.uvs(), remap, MeshData.UV_COMPONENTS),
                permuteFloats(mesh.lightmapUvs(), remap, MeshData.UV_COMPONENTS),
                permuteFloats(mesh.tangents(), remap, MeshData.TANGENT_COMPONENTS),
                permuteFloats(mesh.vertexColors(), remap, MeshData.COLOR_COMPONENTS),
                permuteShorts(mesh.jointIndices(), remap, MeshData.INFLUENCES_PER_VERTEX),
                permuteFloats(mesh.jointWeights(), remap, MeshData.INFLUENCES_PER_VERTEX),
                applyRemapToIndices(indices, remap),
                mesh.submeshes());
    }

    private static void optimizeRange(int[] indices, Submesh submesh, int vertexCount) {
        int[] range = java.util.Arrays.copyOfRange(indices, submesh.indexOffset(),
                submesh.indexOffset() + submesh.indexCount());
        int[] optimized = optimizeIndices(range, vertexCount);
        System.arraycopy(optimized, 0, indices, submesh.indexOffset(), optimized.length);
    }

    public static int[] optimizeIndices(int[] indices, int vertexCount) {
        if (indices.length < 6 || vertexCount <= 0) {
            return indices.clone();
        }
        return new Solver(indices, vertexCount).solve();
    }

    public static int[] fetchOrderPermutation(int[] indices, int vertexCount) {
        int[] remap = new int[vertexCount];
        java.util.Arrays.fill(remap, -1);
        int next = 0;
        for (int index : indices) {
            if (remap[index] < 0) {
                remap[index] = next++;
            }
        }
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (remap[vertex] < 0) {
                remap[vertex] = next++;
            }
        }
        return remap;
    }

    public static float[] permuteFloats(float[] source, int[] remap, int componentsPerVertex) {
        if (source.length == 0) {
            return source;
        }
        float[] destination = new float[source.length];
        for (int vertex = 0; vertex < remap.length; vertex++) {
            System.arraycopy(source, vertex * componentsPerVertex, destination,
                    remap[vertex] * componentsPerVertex, componentsPerVertex);
        }
        return destination;
    }

    public static short[] permuteShorts(short[] source, int[] remap, int componentsPerVertex) {
        if (source.length == 0) {
            return source;
        }
        short[] destination = new short[source.length];
        for (int vertex = 0; vertex < remap.length; vertex++) {
            System.arraycopy(source, vertex * componentsPerVertex, destination,
                    remap[vertex] * componentsPerVertex, componentsPerVertex);
        }
        return destination;
    }

    public static int[] applyRemapToIndices(int[] indices, int[] remap) {
        int[] remapped = new int[indices.length];
        for (int index = 0; index < indices.length; index++) {
            remapped[index] = remap[indices[index]];
        }
        return remapped;
    }

    private static final class Solver {

        private final int[] indices;
        private final int triangleCount;
        private final int[] valence;
        private final int[] cachePosition;
        private final float[] vertexScore;
        private final int[] triangleOffsets;
        private final int[] trianglesByVertex;
        private final float[] triangleScore;
        private final boolean[] emitted;
        private final int[] cache = new int[SCORE_CACHE_SIZE + 3];
        private int cacheCount;
        private int scanCursor;

        private Solver(int[] indices, int vertexCount) {
            this.indices = indices;
            this.triangleCount = indices.length / 3;
            this.valence = new int[vertexCount];
            this.cachePosition = new int[vertexCount];
            this.vertexScore = new float[vertexCount];
            this.triangleOffsets = new int[vertexCount + 1];
            this.trianglesByVertex = new int[triangleCount * 3];
            this.triangleScore = new float[triangleCount];
            this.emitted = new boolean[triangleCount];
            java.util.Arrays.fill(cachePosition, NOT_IN_CACHE);
        }

        private int[] solve() {
            buildAdjacency();
            for (int vertex = 0; vertex < valence.length; vertex++) {
                vertexScore[vertex] = scoreOf(cachePosition[vertex], valence[vertex]);
            }
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                triangleScore[triangle] = sumScores(triangle);
            }
            return emitAll();
        }

        private void buildAdjacency() {
            for (int index : indices) {
                valence[index]++;
            }
            int running = 0;
            for (int vertex = 0; vertex < valence.length; vertex++) {
                triangleOffsets[vertex] = running;
                running += valence[vertex];
            }
            triangleOffsets[valence.length] = running;
            int[] cursor = triangleOffsets.clone();
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                for (int corner = 0; corner < 3; corner++) {
                    trianglesByVertex[cursor[indices[triangle * 3 + corner]]++] = triangle;
                }
            }
        }

        private float sumScores(int triangle) {
            return vertexScore[indices[triangle * 3]] + vertexScore[indices[triangle * 3 + 1]]
                    + vertexScore[indices[triangle * 3 + 2]];
        }

        private int[] emitAll() {
            int[] output = new int[indices.length];
            int written = 0;
            int best = bestTriangle();
            while (best >= 0) {
                emitted[best] = true;
                for (int corner = 0; corner < 3; corner++) {
                    output[written++] = indices[best * 3 + corner];
                }
                touchCache(best);
                refreshScores();
                best = bestTriangle();
            }
            return output;
        }

        private int bestTriangle() {
            int best = -1;
            float bestScore = -1.0f;
            for (int slot = 0; slot < cacheCount; slot++) {
                int vertex = cache[slot];
                for (int entry = triangleOffsets[vertex]; entry < triangleOffsets[vertex + 1]; entry++) {
                    int triangle = trianglesByVertex[entry];
                    if (!emitted[triangle] && triangleScore[triangle] > bestScore) {
                        bestScore = triangleScore[triangle];
                        best = triangle;
                    }
                }
            }
            return best >= 0 ? best : nextUnemitted();
        }

        private int nextUnemitted() {
            while (scanCursor < triangleCount && emitted[scanCursor]) {
                scanCursor++;
            }
            return scanCursor < triangleCount ? scanCursor : -1;
        }

        private void touchCache(int triangle) {
            for (int corner = 2; corner >= 0; corner--) {
                int vertex = indices[triangle * 3 + corner];
                valence[vertex]--;
                moveToFront(vertex);
            }
        }

        private void moveToFront(int vertex) {
            int existing = indexInCache(vertex);
            if (existing < 0) {
                existing = Math.min(cacheCount, cache.length - 1);
                cacheCount = Math.min(cacheCount + 1, cache.length);
            }
            for (int slot = existing; slot > 0; slot--) {
                cache[slot] = cache[slot - 1];
            }
            cache[0] = vertex;
        }

        private int indexInCache(int vertex) {
            for (int slot = 0; slot < cacheCount; slot++) {
                if (cache[slot] == vertex) {
                    return slot;
                }
            }
            return -1;
        }

        private void refreshScores() {
            for (int slot = 0; slot < cacheCount; slot++) {
                int vertex = cache[slot];
                cachePosition[vertex] = slot < SCORE_CACHE_SIZE ? slot : NOT_IN_CACHE;
                vertexScore[vertex] = scoreOf(cachePosition[vertex], valence[vertex]);
            }
            for (int slot = 0; slot < cacheCount; slot++) {
                refreshTrianglesOf(cache[slot]);
            }
        }

        private void refreshTrianglesOf(int vertex) {
            for (int entry = triangleOffsets[vertex]; entry < triangleOffsets[vertex + 1]; entry++) {
                int triangle = trianglesByVertex[entry];
                if (!emitted[triangle]) {
                    triangleScore[triangle] = sumScores(triangle);
                }
            }
        }

        private static float scoreOf(int cachePosition, int remainingValence) {
            if (remainingValence <= 0) {
                return -1.0f;
            }
            float score = 0.0f;
            if (cachePosition >= 0) {
                score = cachePosition < 3
                        ? LAST_TRIANGLE_SCORE
                        : (float) Math.pow(1.0f - (cachePosition - 3.0f) / (SCORE_CACHE_SIZE - 3.0f),
                                CACHE_DECAY_POWER);
            }
            return score + VALENCE_BOOST_SCALE * (float) Math.pow(remainingValence, -VALENCE_BOOST_POWER);
        }
    }
}
