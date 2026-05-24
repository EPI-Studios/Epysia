package fr.epistudio.epysia.render.mesh;

public record Aabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    public static final Aabb UNIT = new Aabb(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f);

    public static Aabb fromPositions(float[] positions) {
        if (positions.length < 3) {
            return UNIT;
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return new Aabb(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
