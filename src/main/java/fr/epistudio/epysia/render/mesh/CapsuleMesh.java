package fr.epistudio.epysia.render.mesh;

import java.util.List;

public final class CapsuleMesh {

    private static final float DEFAULT_RADIUS = 0.4f;
    private static final float DEFAULT_HALF_HEIGHT = 0.9f;
    private static final int DEFAULT_LATITUDE_SEGMENTS = 16;
    private static final int DEFAULT_LONGITUDE_SEGMENTS = 24;

    private CapsuleMesh() {
    }

    public static MeshData data() {
        return data(DEFAULT_RADIUS, DEFAULT_HALF_HEIGHT, DEFAULT_LATITUDE_SEGMENTS, DEFAULT_LONGITUDE_SEGMENTS);
    }

    public static MeshData data(float radius, float halfHeight, int latitudeSegments, int longitudeSegments) {
        int verticesPerRing = longitudeSegments + 1;
        int totalVertices = (latitudeSegments + 1) * verticesPerRing;
        float[] positions = new float[totalVertices * 3];
        float[] normals = new float[totalVertices * 3];
        float[] uvs = new float[totalVertices * 2];
        int equator = latitudeSegments / 2;
        int vertexIndex = 0;
        for (int latitude = 0; latitude <= latitudeSegments; latitude++) {
            float theta = (float) (latitude * Math.PI / latitudeSegments);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float verticalShift = latitude <= equator ? halfHeight : -halfHeight;
            for (int longitude = 0; longitude <= longitudeSegments; longitude++) {
                float phi = (float) (longitude * 2.0 * Math.PI / longitudeSegments);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                float normalX = sinTheta * cosPhi;
                float normalY = cosTheta;
                float normalZ = sinTheta * sinPhi;
                positions[vertexIndex * 3] = normalX * radius;
                positions[vertexIndex * 3 + 1] = normalY * radius + verticalShift;
                positions[vertexIndex * 3 + 2] = normalZ * radius;
                normals[vertexIndex * 3] = normalX;
                normals[vertexIndex * 3 + 1] = normalY;
                normals[vertexIndex * 3 + 2] = normalZ;
                uvs[vertexIndex * 2] = (float) longitude / longitudeSegments;
                uvs[vertexIndex * 2 + 1] = (float) latitude / latitudeSegments;
                vertexIndex++;
            }
        }
        int[] indices = new int[latitudeSegments * longitudeSegments * 6];
        int triangleVertexIndex = 0;
        for (int latitude = 0; latitude < latitudeSegments; latitude++) {
            for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                int corner = latitude * verticesPerRing + longitude;
                int below = corner + verticesPerRing;
                int belowRight = below + 1;
                int right = corner + 1;
                indices[triangleVertexIndex++] = corner;
                indices[triangleVertexIndex++] = belowRight;
                indices[triangleVertexIndex++] = below;
                indices[triangleVertexIndex++] = corner;
                indices[triangleVertexIndex++] = right;
                indices[triangleVertexIndex++] = belowRight;
            }
        }
        return new MeshData(positions, normals, uvs, new float[0], indices, List.of());
    }
}
