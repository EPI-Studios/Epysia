package fr.epistudio.epysia.render.mesh;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexCacheOptimizerTest {

    private static final int CACHE_SIZE = VertexCacheOptimizer.SIMULATED_CACHE_SIZE;

    @Test
    void lowersCacheMissRatioOnACreationOrderMesh() {
        MeshData source = SphereMesh.data(1.0f, 48, 48);

        MeshData optimized = VertexCacheOptimizer.optimize(source);

        float before = VertexCacheOptimizer.averageCacheMissRatio(source.indices(), CACHE_SIZE);
        float after = VertexCacheOptimizer.averageCacheMissRatio(optimized.indices(), CACHE_SIZE);
        System.out.printf("[acmr] sphere 48x48 %.3f -> %.3f%n", before, after);
        assertTrue(after < before,
                "cache miss ratio must drop, was " + before + " and became " + after);
        assertTrue(after < 1.0f, "an optimized closed mesh should stay well under one miss per triangle, got " + after);
    }

    @Test
    void keepsEveryTriangleIntactThroughTheRemap() {
        MeshData source = SphereMesh.data(1.0f, 12, 12);

        MeshData optimized = VertexCacheOptimizer.optimize(source);

        assertEquals(source.indices().length, optimized.indices().length, "triangle count must not change");
        assertEquals(source.vertexCount(), optimized.vertexCount(), "vertex count must not change");
        assertEquals(trianglesOf(source), trianglesOf(optimized),
                "every triangle must keep the same three vertex positions in the same winding");
    }

    @Test
    void keepsSubmeshRangesAddressingTheSameTriangles() {
        MeshData source = twoSubmeshQuadStrip();

        MeshData optimized = VertexCacheOptimizer.optimize(source);

        assertEquals(source.submeshes(), optimized.submeshes(), "submesh ranges must be preserved");
        for (int submesh = 0; submesh < source.submeshes().size(); submesh++) {
            assertEquals(trianglesOfSubmesh(source, submesh), trianglesOfSubmesh(optimized, submesh),
                    "submesh " + submesh + " must keep its own triangles");
        }
    }

    private static MeshData twoSubmeshQuadStrip() {
        int columns = 8;
        float[] positions = new float[(columns + 1) * 2 * 3];
        float[] normals = new float[positions.length];
        List<Integer> indices = new ArrayList<>();
        for (int column = 0; column <= columns; column++) {
            writeVertex(positions, normals, column * 2, column, 0.0f);
            writeVertex(positions, normals, column * 2 + 1, column, 1.0f);
        }
        for (int column = 0; column < columns; column++) {
            int base = column * 2;
            indices.add(base);
            indices.add(base + 1);
            indices.add(base + 2);
            indices.add(base + 1);
            indices.add(base + 3);
            indices.add(base + 2);
        }
        int[] flat = indices.stream().mapToInt(Integer::intValue).toArray();
        int half = flat.length / 2 / 3 * 3;
        return new MeshData(positions, normals, new float[0], new float[0], new short[0], new float[0], flat,
                List.of(new Submesh(0, half, 0), new Submesh(half, flat.length - half, 1)));
    }

    private static void writeVertex(float[] positions, float[] normals, int vertex, float x, float y) {
        positions[vertex * 3] = x;
        positions[vertex * 3 + 1] = y;
        positions[vertex * 3 + 2] = 0.0f;
        normals[vertex * 3 + 2] = 1.0f;
    }

    private static List<String> trianglesOf(MeshData mesh) {
        return trianglesIn(mesh, 0, mesh.indices().length);
    }

    private static List<String> trianglesOfSubmesh(MeshData mesh, int submesh) {
        Submesh range = mesh.submeshes().get(submesh);
        return trianglesIn(mesh, range.indexOffset(), range.indexOffset() + range.indexCount());
    }

    private static List<String> trianglesIn(MeshData mesh, int from, int to) {
        List<String> triangles = new ArrayList<>();
        for (int index = from; index + 2 < to; index += 3) {
            triangles.add(cornerText(mesh, index) + "|" + cornerText(mesh, index + 1)
                    + "|" + cornerText(mesh, index + 2));
        }
        triangles.sort(String::compareTo);
        return triangles;
    }

    private static String cornerText(MeshData mesh, int index) {
        int vertex = mesh.indices()[index];
        return String.format("%.4f,%.4f,%.4f", mesh.positions()[vertex * 3],
                mesh.positions()[vertex * 3 + 1], mesh.positions()[vertex * 3 + 2]);
    }
}
