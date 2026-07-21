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
}
