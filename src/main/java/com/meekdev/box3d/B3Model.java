package com.meekdev.box3d;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** triangle soup pulled from a model file, feeds hulls, meshes and decompositions */
public record B3Model(float[] vertices, int[] indices) {

    /** parses wavefront obj, triangulates fans, ignores everything but v and f */
    public static B3Model loadObj(InputStream stream) throws IOException {
        List<float[]> verts = new ArrayList<>();
        List<int[]> tris = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && parts[0].equals("v")) {
                    verts.add(new float[] {Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                } else if (parts.length >= 4 && parts[0].equals("f")) {
                    int[] face = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        String token = parts[i].split("/")[0];
                        int index = Integer.parseInt(token);
                        face[i - 1] = index > 0 ? index - 1 : verts.size() + index;
                    }
                    for (int i = 2; i < face.length; i++) {
                        tris.add(new int[] {face[0], face[i - 1], face[i]});
                    }
                }
            }
        }
        float[] vertices = new float[verts.size() * 3];
        for (int i = 0; i < verts.size(); i++) {
            System.arraycopy(verts.get(i), 0, vertices, i * 3, 3);
        }
        int[] indices = new int[tris.size() * 3];
        for (int i = 0; i < tris.size(); i++) {
            System.arraycopy(tris.get(i), 0, indices, i * 3, 3);
        }
        if (indices.length == 0) {
            throw new IOException("no triangles in obj");
        }
        return new B3Model(vertices, indices);
    }

    /** single convex hull over the whole model, right for convex-ish props */
    public B3Hull toHull(int maxVertices) {
        return B3Hull.bake(vertices, maxVertices);
    }

    /** static triangle mesh, exact but static or kinematic only in practice */
    public B3Mesh toMesh() {
        return B3Mesh.bake(vertices, indices);
    }

    /** industrial quality convex decomposition through v-hacd

        voxel resolution drives quality and cost, 100k is a good middle,
        throws when the optional vhacd native is not packaged */
    public List<B3Hull> decomposeExact(int maxHulls, int voxelResolution, int maxVerticesPerHull) {
        if (!NativeLoader.hasVhacd()) {
            throw new IllegalStateException("vhacd native not available");
        }
        try (java.lang.foreign.Arena temp = java.lang.foreign.Arena.ofConfined()) {
            var pointsSeg = temp.allocateFrom(java.lang.foreign.ValueLayout.JAVA_FLOAT, vertices);
            var trisSeg = temp.allocateFrom(java.lang.foreign.ValueLayout.JAVA_INT, indices);
            var result = com.meekdev.box3d.ffi.vhacd.vhacd_wrapper_h.bvh_compute(
                    pointsSeg, vertices.length / 3, trisSeg, indices.length / 3,
                    maxHulls, voxelResolution, maxVerticesPerHull);
            if (result.equals(java.lang.foreign.MemorySegment.NULL)) {
                throw new IllegalArgumentException("vhacd failed on this mesh");
            }
            try {
                var res = result.reinterpret(com.meekdev.box3d.ffi.vhacd.bvh_result.sizeof());
                int count = com.meekdev.box3d.ffi.vhacd.bvh_result.hullCount(res);
                var hullArray = com.meekdev.box3d.ffi.vhacd.bvh_result.hulls(res)
                        .reinterpret(com.meekdev.box3d.ffi.vhacd.bvh_hull.sizeof() * count);
                List<B3Hull> hulls = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    var entry = com.meekdev.box3d.ffi.vhacd.bvh_hull.asSlice(hullArray, i);
                    int n = com.meekdev.box3d.ffi.vhacd.bvh_hull.pointCount(entry);
                    float[] cloud = com.meekdev.box3d.ffi.vhacd.bvh_hull.points(entry)
                            .reinterpret((long) n * 3 * Float.BYTES)
                            .toArray(java.lang.foreign.ValueLayout.JAVA_FLOAT);
                    try {
                        hulls.add(B3Hull.bake(cloud, Math.min(n, maxVerticesPerHull)));
                    } catch (IllegalArgumentException e) {
                        // degenerate sliver, drop it
                    }
                }
                return hulls;
            } finally {
                com.meekdev.box3d.ffi.vhacd.vhacd_wrapper_h.bvh_free(result);
            }
        }
    }

    /** approximate convex decomposition for concave dynamic props

        clusters triangles by proximity and hulls each cluster, nowhere near
        vhacd quality but good enough for gameplay, hulls overlap a little */
    public List<B3Hull> decompose(int maxHulls, int maxVerticesPerHull) {
        int triCount = indices.length / 3;
        int k = Math.max(1, Math.min(maxHulls, triCount));

        // triangle centroids
        float[][] centroids = new float[triCount][3];
        for (int t = 0; t < triCount; t++) {
            for (int c = 0; c < 3; c++) {
                int vi = indices[t * 3 + c] * 3;
                centroids[t][0] += vertices[vi] / 3f;
                centroids[t][1] += vertices[vi + 1] / 3f;
                centroids[t][2] += vertices[vi + 2] / 3f;
            }
        }

        // deterministic k means, seeds spread along the triangle list
        float[][] seeds = new float[k][3];
        for (int i = 0; i < k; i++) {
            seeds[i] = centroids[(int) ((long) i * triCount / k)].clone();
        }
        int[] cluster = new int[triCount];
        for (int iteration = 0; iteration < 12; iteration++) {
            for (int t = 0; t < triCount; t++) {
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < k; i++) {
                    double dx = centroids[t][0] - seeds[i][0];
                    double dy = centroids[t][1] - seeds[i][1];
                    double dz = centroids[t][2] - seeds[i][2];
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        cluster[t] = i;
                    }
                }
            }
            float[][] sums = new float[k][3];
            int[] counts = new int[k];
            for (int t = 0; t < triCount; t++) {
                sums[cluster[t]][0] += centroids[t][0];
                sums[cluster[t]][1] += centroids[t][1];
                sums[cluster[t]][2] += centroids[t][2];
                counts[cluster[t]]++;
            }
            for (int i = 0; i < k; i++) {
                if (counts[i] > 0) {
                    seeds[i][0] = sums[i][0] / counts[i];
                    seeds[i][1] = sums[i][1] / counts[i];
                    seeds[i][2] = sums[i][2] / counts[i];
                }
            }
        }

        List<B3Hull> hulls = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<Float> points = new ArrayList<>();
            for (int t = 0; t < triCount; t++) {
                if (cluster[t] != i) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    int vi = indices[t * 3 + c] * 3;
                    points.add(vertices[vi]);
                    points.add(vertices[vi + 1]);
                    points.add(vertices[vi + 2]);
                }
            }
            if (points.size() < 12) {
                continue;
            }
            float[] cloud = new float[points.size()];
            for (int j = 0; j < cloud.length; j++) {
                cloud[j] = points.get(j);
            }
            try {
                hulls.add(B3Hull.bake(cloud, maxVerticesPerHull));
            } catch (IllegalArgumentException e) {
                // degenerate cluster, flat or tiny, skip it
            }
        }
        if (hulls.isEmpty()) {
            hulls.add(toHull(maxVerticesPerHull));
        }
        return hulls;
    }
}
