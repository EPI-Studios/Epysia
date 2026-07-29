package fr.epistudio.epysia.render.baking;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class LightmapOccluders {

    private static final float EPSILON = 1.0e-6f;

    private final List<float[]> triangles = new ArrayList<>();

    void add(LightmapGeometry geometry) {
        float[] positions = geometry.positions();
        int[] indices = geometry.indices();
        for (int index = 0; index + 2 < indices.length; index += 3) {
            triangles.add(new float[]{
                    positions[indices[index] * 3], positions[indices[index] * 3 + 1], positions[indices[index] * 3 + 2],
                    positions[indices[index + 1] * 3], positions[indices[index + 1] * 3 + 1],
                    positions[indices[index + 1] * 3 + 2],
                    positions[indices[index + 2] * 3], positions[indices[index + 2] * 3 + 1],
                    positions[indices[index + 2] * 3 + 2]
            });
        }
    }

    int triangleCount() {
        return triangles.size();
    }

    boolean occluded(Vector3f origin, Vector3f direction, float maxDistance) {
        for (float[] triangle : triangles) {
            float hit = intersect(origin, direction, triangle);
            if (hit > EPSILON && hit < maxDistance) {
                return true;
            }
        }
        return false;
    }

    private static float intersect(Vector3f origin, Vector3f direction, float[] triangle) {
        float edge1X = triangle[3] - triangle[0];
        float edge1Y = triangle[4] - triangle[1];
        float edge1Z = triangle[5] - triangle[2];
        float edge2X = triangle[6] - triangle[0];
        float edge2Y = triangle[7] - triangle[1];
        float edge2Z = triangle[8] - triangle[2];
        float pvecX = direction.y * edge2Z - direction.z * edge2Y;
        float pvecY = direction.z * edge2X - direction.x * edge2Z;
        float pvecZ = direction.x * edge2Y - direction.y * edge2X;
        float determinant = edge1X * pvecX + edge1Y * pvecY + edge1Z * pvecZ;
        if (Math.abs(determinant) < EPSILON) {
            return -1.0f;
        }
        float inverse = 1.0f / determinant;
        float tvecX = origin.x - triangle[0];
        float tvecY = origin.y - triangle[1];
        float tvecZ = origin.z - triangle[2];
        float barycentricU = (tvecX * pvecX + tvecY * pvecY + tvecZ * pvecZ) * inverse;
        if (barycentricU < 0.0f || barycentricU > 1.0f) {
            return -1.0f;
        }
        float qvecX = tvecY * edge1Z - tvecZ * edge1Y;
        float qvecY = tvecZ * edge1X - tvecX * edge1Z;
        float qvecZ = tvecX * edge1Y - tvecY * edge1X;
        float barycentricV = (direction.x * qvecX + direction.y * qvecY + direction.z * qvecZ) * inverse;
        if (barycentricV < 0.0f || barycentricU + barycentricV > 1.0f) {
            return -1.0f;
        }
        return (edge2X * qvecX + edge2Y * qvecY + edge2Z * qvecZ) * inverse;
    }
}
