package fr.epistudio.epysia.render.mesh;

public final class TangentCalculator {

    private TangentCalculator() {
    }

    public static float[] compute(float[] positions, float[] normals, float[] uvs, int[] indices) {
        int vertexCount = positions.length / MeshData.POSITION_COMPONENTS;
        float[] tangents = new float[vertexCount * MeshData.TANGENT_COMPONENTS];
        if (uvs.length == 0) {
            fillDefaultTangents(tangents);
            return tangents;
        }
        accumulateTangents(positions, uvs, indices, tangents);
        orthogonalizeAndNormalize(normals, tangents);
        return tangents;
    }

    private static void fillDefaultTangents(float[] tangents) {
        for (int i = 0; i < tangents.length; i += MeshData.TANGENT_COMPONENTS) {
            tangents[i] = 1.0f;
            tangents[i + 1] = 0.0f;
            tangents[i + 2] = 0.0f;
        }
    }

    private static void accumulateTangents(float[] positions, float[] uvs, int[] indices, float[] tangents) {
        for (int triangle = 0; triangle < indices.length; triangle += 3) {
            int i0 = indices[triangle];
            int i1 = indices[triangle + 1];
            int i2 = indices[triangle + 2];
            addTriangleTangent(positions, uvs, tangents, i0, i1, i2);
        }
    }

    private static void addTriangleTangent(float[] positions, float[] uvs, float[] tangents, int i0, int i1, int i2) {
        float p0x = positions[i0 * 3], p0y = positions[i0 * 3 + 1], p0z = positions[i0 * 3 + 2];
        float p1x = positions[i1 * 3], p1y = positions[i1 * 3 + 1], p1z = positions[i1 * 3 + 2];
        float p2x = positions[i2 * 3], p2y = positions[i2 * 3 + 1], p2z = positions[i2 * 3 + 2];
        float u0 = uvs[i0 * 2], v0 = uvs[i0 * 2 + 1];
        float u1 = uvs[i1 * 2], v1 = uvs[i1 * 2 + 1];
        float u2 = uvs[i2 * 2], v2 = uvs[i2 * 2 + 1];
        float e1x = p1x - p0x, e1y = p1y - p0y, e1z = p1z - p0z;
        float e2x = p2x - p0x, e2y = p2y - p0y, e2z = p2z - p0z;
        float du1 = u1 - u0, dv1 = v1 - v0;
        float du2 = u2 - u0, dv2 = v2 - v0;
        float denominator = du1 * dv2 - du2 * dv1;
        if (Math.abs(denominator) < 1.0e-8f) {
            return;
        }
        float invDenominator = 1.0f / denominator;
        float tx = (e1x * dv2 - e2x * dv1) * invDenominator;
        float ty = (e1y * dv2 - e2y * dv1) * invDenominator;
        float tz = (e1z * dv2 - e2z * dv1) * invDenominator;
        accumulate(tangents, i0, tx, ty, tz);
        accumulate(tangents, i1, tx, ty, tz);
        accumulate(tangents, i2, tx, ty, tz);
    }

    private static void accumulate(float[] tangents, int vertexIndex, float x, float y, float z) {
        int base = vertexIndex * MeshData.TANGENT_COMPONENTS;
        tangents[base] += x;
        tangents[base + 1] += y;
        tangents[base + 2] += z;
    }

    private static void orthogonalizeAndNormalize(float[] normals, float[] tangents) {
        int vertexCount = tangents.length / MeshData.TANGENT_COMPONENTS;
        for (int i = 0; i < vertexCount; i++) {
            int base = i * MeshData.TANGENT_COMPONENTS;
            float nx = normals[base], ny = normals[base + 1], nz = normals[base + 2];
            float tx = tangents[base], ty = tangents[base + 1], tz = tangents[base + 2];
            float dot = nx * tx + ny * ty + nz * tz;
            tx -= nx * dot;
            ty -= ny * dot;
            tz -= nz * dot;
            float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (length > 1.0e-6f) {
                tangents[base] = tx / length;
                tangents[base + 1] = ty / length;
                tangents[base + 2] = tz / length;
            } else {
                tangents[base] = 1.0f;
                tangents[base + 1] = 0.0f;
                tangents[base + 2] = 0.0f;
            }
        }
    }
}
