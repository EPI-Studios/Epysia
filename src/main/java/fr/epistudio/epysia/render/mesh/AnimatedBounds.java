package fr.epistudio.epysia.render.mesh;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class AnimatedBounds {
    private static final int REFRESH_INTERVAL_FRAMES =
            Integer.getInteger("epysia.animation.boundsInterval", 12);
    private static final float DILATION_FRACTION = 0.15f;

    private Aabb bounds = Aabb.UNIT;
    private boolean computed;
    private int framesUntilRefresh;

    Aabb refresh(Matrix4f[] skinningMatrices, Aabb bindBounds, Vector3f scratchCorner) {
        framesUntilRefresh--;
        if (computed && framesUntilRefresh > 0) {
            return bounds;
        }
        framesUntilRefresh = REFRESH_INTERVAL_FRAMES;
        computed = true;
        bounds = dilate(union(skinningMatrices, bindBounds, scratchCorner));
        return bounds;
    }

    boolean computed() {
        return computed;
    }

    Aabb bounds() {
        return bounds;
    }

    private static Aabb union(Matrix4f[] skinningMatrices, Aabb bindBounds, Vector3f scratchCorner) {
        float[] extremes = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (Matrix4f skinningMatrix : skinningMatrices) {
            uniteTransformedCorners(skinningMatrix, bindBounds, scratchCorner, extremes);
        }
        return new Aabb(extremes[0], extremes[1], extremes[2], extremes[3], extremes[4], extremes[5]);
    }

    private static void uniteTransformedCorners(Matrix4f skinningMatrix, Aabb bounds,
                                                Vector3f scratchCorner, float[] extremes) {
        for (int index = 0; index < 8; index++) {
            float x = (index & 1) == 0 ? bounds.minX() : bounds.maxX();
            float y = (index & 2) == 0 ? bounds.minY() : bounds.maxY();
            float z = (index & 4) == 0 ? bounds.minZ() : bounds.maxZ();
            skinningMatrix.transformPosition(scratchCorner.set(x, y, z));
            extremes[0] = Math.min(extremes[0], scratchCorner.x);
            extremes[1] = Math.min(extremes[1], scratchCorner.y);
            extremes[2] = Math.min(extremes[2], scratchCorner.z);
            extremes[3] = Math.max(extremes[3], scratchCorner.x);
            extremes[4] = Math.max(extremes[4], scratchCorner.y);
            extremes[5] = Math.max(extremes[5], scratchCorner.z);
        }
    }

    private static Aabb dilate(Aabb source) {
        float marginX = (source.maxX() - source.minX()) * DILATION_FRACTION * 0.5f;
        float marginY = (source.maxY() - source.minY()) * DILATION_FRACTION * 0.5f;
        float marginZ = (source.maxZ() - source.minZ()) * DILATION_FRACTION * 0.5f;
        return new Aabb(source.minX() - marginX, source.minY() - marginY, source.minZ() - marginZ,
                source.maxX() + marginX, source.maxY() + marginY, source.maxZ() + marginZ);
    }
}
