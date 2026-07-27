package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.DrawCommand;

record ShadowCaster(DrawCommand command, long identity, long signature, boolean animated,
                    float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    static ShadowCaster unbounded(DrawCommand command, long identity, long signature, boolean animated) {
        return new ShadowCaster(command, identity, signature, animated,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    boolean bounded() {
        return minX != Float.NEGATIVE_INFINITY;
    }

    boolean intersectsSphere(float centerX, float centerY, float centerZ, float radius) {
        if (!bounded()) {
            return true;
        }
        float dx = Math.max(minX - centerX, Math.max(0.0f, centerX - maxX));
        float dy = Math.max(minY - centerY, Math.max(0.0f, centerY - maxY));
        float dz = Math.max(minZ - centerZ, Math.max(0.0f, centerZ - maxZ));
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
