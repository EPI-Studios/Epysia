package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class SphereMesh {

    private static final float DEFAULT_RADIUS = 0.5f;
    private static final int DEFAULT_LATITUDE_SEGMENTS = 32;
    private static final int DEFAULT_LONGITUDE_SEGMENTS = 48;

    private SphereMesh() {
    }

    public static MeshData data() {
        return data(DEFAULT_RADIUS, DEFAULT_LATITUDE_SEGMENTS, DEFAULT_LONGITUDE_SEGMENTS);
    }

    public static MeshData data(float radius, int latitudeSegments, int longitudeSegments) {
        int verticesPerRing = longitudeSegments + 1;
        int totalVertices = (latitudeSegments + 1) * verticesPerRing;
        float[] positions = new float[totalVertices * 3];
        float[] normals = new float[totalVertices * 3];
        float[] uvs = new float[totalVertices * 2];
        fillVertices(radius, latitudeSegments, longitudeSegments, positions, normals, uvs);
        return new MeshData(positions, normals, uvs, new float[0],
                buildIndices(latitudeSegments, longitudeSegments), List.of());
    }

    private static void fillVertices(float radius, int latitudeSegments, int longitudeSegments,
                                     float[] positions, float[] normals, float[] uvs) {
        int vertexIndex = 0;
        for (int latitude = 0; latitude <= latitudeSegments; latitude++) {
            float theta = (float) (latitude * Math.PI / latitudeSegments);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            for (int longitude = 0; longitude <= longitudeSegments; longitude++) {
                float phi = (float) (longitude * 2.0 * Math.PI / longitudeSegments);
                float normalX = sinTheta * (float) Math.cos(phi);
                float normalY = cosTheta;
                float normalZ = sinTheta * (float) Math.sin(phi);
                positions[vertexIndex * 3] = normalX * radius;
                positions[vertexIndex * 3 + 1] = normalY * radius;
                positions[vertexIndex * 3 + 2] = normalZ * radius;
                normals[vertexIndex * 3] = normalX;
                normals[vertexIndex * 3 + 1] = normalY;
                normals[vertexIndex * 3 + 2] = normalZ;
                uvs[vertexIndex * 2] = (float) longitude / longitudeSegments;
                uvs[vertexIndex * 2 + 1] = 1.0f - (float) latitude / latitudeSegments;
                vertexIndex++;
            }
        }
    }

    private static int[] buildIndices(int latitudeSegments, int longitudeSegments) {
        int verticesPerRing = longitudeSegments + 1;
        int[] indices = new int[latitudeSegments * longitudeSegments * 6];
        int cursor = 0;
        for (int latitude = 0; latitude < latitudeSegments; latitude++) {
            for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                int corner = latitude * verticesPerRing + longitude;
                int below = corner + verticesPerRing;
                indices[cursor++] = corner;
                indices[cursor++] = below + 1;
                indices[cursor++] = below;
                indices[cursor++] = corner;
                indices[cursor++] = corner + 1;
                indices[cursor++] = below + 1;
            }
        }
        return indices;
    }
}
