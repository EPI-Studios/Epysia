package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class QuadMesh {

    private QuadMesh() {
    }

    public static MeshData data(float halfSize, int segments) {
        return data(halfSize, segments, segments);
    }

    public static MeshData data(float halfSize, int widthSegments, int heightSegments) {
        int columns = Math.max(1, widthSegments);
        int rows = Math.max(1, heightSegments);
        int vertexCount = (columns + 1) * (rows + 1);
        float[] positions = new float[vertexCount * MeshData.POSITION_COMPONENTS];
        float[] normals = new float[vertexCount * MeshData.NORMAL_COMPONENTS];
        float[] uvs = new float[vertexCount * MeshData.UV_COMPONENTS];
        fillVertices(halfSize, columns, rows, positions, normals, uvs);
        return new MeshData(positions, normals, uvs, new float[0], new short[0], new float[0],
                buildIndices(columns, rows), List.of());
    }

    private static void fillVertices(float halfSize, int columns, int rows,
                                     float[] positions, float[] normals, float[] uvs) {
        int verticesPerRow = columns + 1;
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= columns; column++) {
                int vertex = row * verticesPerRow + column;
                float alongX = column / (float) columns;
                float alongY = row / (float) rows;
                positions[vertex * MeshData.POSITION_COMPONENTS] = (alongX * 2.0f - 1.0f) * halfSize;
                positions[vertex * MeshData.POSITION_COMPONENTS + 1] = (alongY * 2.0f - 1.0f) * halfSize;
                normals[vertex * MeshData.NORMAL_COMPONENTS + 2] = 1.0f;
                uvs[vertex * MeshData.UV_COMPONENTS] = alongX;
                uvs[vertex * MeshData.UV_COMPONENTS + 1] = 1.0f - alongY;
            }
        }
    }

    private static int[] buildIndices(int columns, int rows) {
        int verticesPerRow = columns + 1;
        int[] indices = new int[columns * rows * 6];
        int cursor = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int bottomLeft = row * verticesPerRow + column;
                int bottomRight = bottomLeft + 1;
                int topLeft = bottomLeft + verticesPerRow;
                int topRight = topLeft + 1;
                indices[cursor++] = bottomLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = topRight;
                indices[cursor++] = bottomLeft;
                indices[cursor++] = topRight;
                indices[cursor++] = topLeft;
            }
        }
        return indices;
    }
}
