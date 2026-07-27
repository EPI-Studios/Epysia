package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class PlaneMesh {

    private PlaneMesh() {
    }

    public static MeshData data(float halfSize, float uvTileCount) {
        return data(halfSize, uvTileCount, 1);
    }

    public static MeshData data(float halfSize, float uvTileCount, int segments) {
        int steps = Math.max(1, segments);
        int vertexCount = (steps + 1) * (steps + 1);
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        fillVertices(halfSize, uvTileCount, steps, positions, normals, uvs);
        return new MeshData(positions, normals, uvs, new float[0], new short[0], new float[0],
                buildIndices(steps), List.of());
    }

    private static void fillVertices(float halfSize, float uvTileCount, int steps,
                                     float[] positions, float[] normals, float[] uvs) {
        int verticesPerRow = steps + 1;
        for (int row = 0; row < verticesPerRow; row++) {
            for (int column = 0; column < verticesPerRow; column++) {
                int vertex = row * verticesPerRow + column;
                float alongX = column / (float) steps;
                float alongZ = row / (float) steps;
                positions[vertex * 3] = (alongX * 2.0f - 1.0f) * halfSize;
                positions[vertex * 3 + 1] = 0.0f;
                positions[vertex * 3 + 2] = (1.0f - alongZ * 2.0f) * halfSize;
                normals[vertex * 3 + 1] = 1.0f;
                uvs[vertex * 2] = alongX * uvTileCount;
                uvs[vertex * 2 + 1] = alongZ * uvTileCount;
            }
        }
    }

    private static int[] buildIndices(int steps) {
        int verticesPerRow = steps + 1;
        int[] indices = new int[steps * steps * 6];
        int cursor = 0;
        for (int row = 0; row < steps; row++) {
            for (int column = 0; column < steps; column++) {
                int topLeft = row * verticesPerRow + column;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + verticesPerRow;
                int bottomRight = bottomLeft + 1;
                indices[cursor++] = topLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = bottomLeft;
                indices[cursor++] = topLeft;
                indices[cursor++] = topRight;
                indices[cursor++] = bottomRight;
            }
        }
        return indices;
    }
}
