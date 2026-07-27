package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class CubeMeshGrid {

    private static final float[][] FACE_NORMALS = {
            {0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, -1.0f},
            {0.0f, 1.0f, 0.0f}, {0.0f, -1.0f, 0.0f},
            {1.0f, 0.0f, 0.0f}, {-1.0f, 0.0f, 0.0f}
    };
    private static final float[][] FACE_RIGHT = {
            {1.0f, 0.0f, 0.0f}, {-1.0f, 0.0f, 0.0f},
            {1.0f, 0.0f, 0.0f}, {1.0f, 0.0f, 0.0f},
            {0.0f, 0.0f, -1.0f}, {0.0f, 0.0f, 1.0f}
    };
    private static final float[][] FACE_UP = {
            {0.0f, 1.0f, 0.0f}, {0.0f, 1.0f, 0.0f},
            {0.0f, 0.0f, -1.0f}, {0.0f, 0.0f, 1.0f},
            {0.0f, 1.0f, 0.0f}, {0.0f, 1.0f, 0.0f}
    };
    private static final int FACE_COUNT = 6;

    private CubeMeshGrid() {
    }

    public static MeshData data(float halfSize, int segments) {
        int steps = Math.max(1, segments);
        int verticesPerFace = (steps + 1) * (steps + 1);
        float[] positions = new float[FACE_COUNT * verticesPerFace * 3];
        float[] normals = new float[FACE_COUNT * verticesPerFace * 3];
        float[] uvs = new float[FACE_COUNT * verticesPerFace * 2];
        int[] indices = new int[FACE_COUNT * steps * steps * 6];
        for (int face = 0; face < FACE_COUNT; face++) {
            fillFace(face, halfSize, steps, positions, normals, uvs);
            fillFaceIndices(face, steps, verticesPerFace, indices);
        }
        return new MeshData(positions, normals, uvs, new float[0], new short[0], new float[0],
                indices, List.of());
    }

    private static void fillFace(int face, float halfSize, int steps,
                                 float[] positions, float[] normals, float[] uvs) {
        int verticesPerRow = steps + 1;
        int base = face * verticesPerRow * verticesPerRow;
        for (int row = 0; row < verticesPerRow; row++) {
            for (int column = 0; column < verticesPerRow; column++) {
                int vertex = base + row * verticesPerRow + column;
                float alongRight = column / (float) steps * 2.0f - 1.0f;
                float alongUp = 1.0f - row / (float) steps * 2.0f;
                writePosition(face, halfSize, alongRight, alongUp, positions, vertex);
                normals[vertex * 3] = FACE_NORMALS[face][0];
                normals[vertex * 3 + 1] = FACE_NORMALS[face][1];
                normals[vertex * 3 + 2] = FACE_NORMALS[face][2];
                uvs[vertex * 2] = column / (float) steps;
                uvs[vertex * 2 + 1] = 1.0f - row / (float) steps;
            }
        }
    }

    private static void writePosition(int face, float halfSize, float alongRight, float alongUp,
                                      float[] positions, int vertex) {
        for (int axis = 0; axis < 3; axis++) {
            positions[vertex * 3 + axis] = halfSize * (FACE_NORMALS[face][axis]
                    + FACE_RIGHT[face][axis] * alongRight
                    + FACE_UP[face][axis] * alongUp);
        }
    }

    private static void fillFaceIndices(int face, int steps, int verticesPerFace, int[] indices) {
        int verticesPerRow = steps + 1;
        int vertexBase = face * verticesPerFace;
        int cursor = face * steps * steps * 6;
        for (int row = 0; row < steps; row++) {
            for (int column = 0; column < steps; column++) {
                int topLeft = vertexBase + row * verticesPerRow + column;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + verticesPerRow;
                int bottomRight = bottomLeft + 1;
                indices[cursor++] = topLeft;
                indices[cursor++] = bottomLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = topLeft;
                indices[cursor++] = bottomRight;
                indices[cursor++] = topRight;
            }
        }
    }
}
