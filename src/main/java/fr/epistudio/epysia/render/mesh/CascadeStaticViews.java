package fr.epistudio.epysia.render.mesh;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class CascadeStaticViews implements ShadowStaticViews {
    private static final float TEXEL_TOLERANCE = 0.01f;
    private static final int MAXIMUM_EXPOSED_FRACTION = 4;

    private final Matrix4f[] currentMatrices;
    private final Matrix4f[] bakedMatrices;
    private final int mapSize;
    private final Vector3f scratchCentre = new Vector3f();
    private final Vector3f scratchHalfExtent = new Vector3f();

    CascadeStaticViews(Matrix4f[] currentMatrices, int mapSize) {
        this.currentMatrices = currentMatrices;
        this.mapSize = mapSize;
        this.bakedMatrices = new Matrix4f[currentMatrices.length];
        for (int layer = 0; layer < currentMatrices.length; layer++) {
            bakedMatrices[layer] = new Matrix4f();
        }
    }

    @Override
    public void markBaked(int layer) {
        bakedMatrices[layer].set(currentMatrices[layer]);
    }

    @Override
    public ShadowLayerTranslation translationSinceBake(int layer) {
        Matrix4f baked = bakedMatrices[layer];
        Matrix4f current = currentMatrices[layer];
        if (!sharesProjection(baked, current)) {
            return ShadowLayerTranslation.rebuild();
        }
        if (current.m32() != baked.m32()) {
            return ShadowLayerTranslation.rebuild();
        }
        float texelX = (current.m30() - baked.m30()) * mapSize * 0.5f;
        float texelY = (current.m31() - baked.m31()) * mapSize * 0.5f;
        if (!integral(texelX) || !integral(texelY)) {
            return ShadowLayerTranslation.rebuild();
        }
        int offsetX = Math.round(texelX);
        int offsetY = Math.round(texelY);
        int limit = mapSize / MAXIMUM_EXPOSED_FRACTION;
        if (Math.abs(offsetX) >= limit || Math.abs(offsetY) >= limit) {
            return ShadowLayerTranslation.rebuild();
        }
        return ShadowLayerTranslation.of(offsetX, offsetY);
    }

    @Override
    public boolean casterTouchesExposedRegion(int layer, ShadowLayerTranslation translation, ShadowCaster caster) {
        if (!caster.bounded()) {
            return true;
        }
        readBounds(caster);
        Matrix4f matrix = currentMatrices[layer];
        return axisOverlapsBand(
                projectedCentre(matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30()),
                projectedRadius(matrix.m00(), matrix.m10(), matrix.m20()), translation.texelX())
                || axisOverlapsBand(
                projectedCentre(matrix.m01(), matrix.m11(), matrix.m21(), matrix.m31()),
                projectedRadius(matrix.m01(), matrix.m11(), matrix.m21()), translation.texelY());
    }

    private void readBounds(ShadowCaster caster) {
        scratchCentre.set(
                0.5f * (caster.minX() + caster.maxX()),
                0.5f * (caster.minY() + caster.maxY()),
                0.5f * (caster.minZ() + caster.maxZ()));
        scratchHalfExtent.set(
                0.5f * (caster.maxX() - caster.minX()),
                0.5f * (caster.maxY() - caster.minY()),
                0.5f * (caster.maxZ() - caster.minZ()));
    }

    private float projectedCentre(float axisX, float axisY, float axisZ, float offset) {
        return axisX * scratchCentre.x + axisY * scratchCentre.y + axisZ * scratchCentre.z + offset;
    }

    private float projectedRadius(float axisX, float axisY, float axisZ) {
        return Math.abs(axisX) * scratchHalfExtent.x
                + Math.abs(axisY) * scratchHalfExtent.y
                + Math.abs(axisZ) * scratchHalfExtent.z;
    }

    private boolean axisOverlapsBand(float centre, float radius, int offset) {
        if (offset == 0) {
            return false;
        }
        float minTexel = (centre - radius + 1.0f) * 0.5f * mapSize;
        float maxTexel = (centre + radius + 1.0f) * 0.5f * mapSize;
        if (offset > 0) {
            return minTexel < offset && maxTexel >= 0.0f;
        }
        return maxTexel > mapSize + offset && minTexel <= mapSize;
    }

    private static boolean integral(float value) {
        return Math.abs(value - Math.round(value)) < TEXEL_TOLERANCE;
    }

    private static boolean sharesProjection(Matrix4f first, Matrix4f second) {
        return first.m00() == second.m00() && first.m01() == second.m01() && first.m02() == second.m02()
                && first.m10() == second.m10() && first.m11() == second.m11() && first.m12() == second.m12()
                && first.m20() == second.m20() && first.m21() == second.m21() && first.m22() == second.m22()
                && first.m03() == second.m03() && first.m13() == second.m13() && first.m23() == second.m23()
                && first.m33() == second.m33();
    }
}
