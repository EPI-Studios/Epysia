package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class PlaneMesh {

    private static final int[] INDICES = {0, 1, 2, 0, 2, 3};
    private static final float[] NORMALS = {
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f
    };

    private PlaneMesh() {
    }

    public static MeshData data(float halfSize, float uvTileCount) {
        float[] positions = {
                -halfSize, 0.0f,  halfSize,
                 halfSize, 0.0f,  halfSize,
                 halfSize, 0.0f, -halfSize,
                -halfSize, 0.0f, -halfSize
        };
        float[] uvs = {
                0.0f,         0.0f,
                uvTileCount,  0.0f,
                uvTileCount,  uvTileCount,
                0.0f,         uvTileCount
        };
        return new MeshData(positions, NORMALS.clone(), uvs, new float[0], new short[0], new float[0], INDICES.clone(), List.of());
    }
}
