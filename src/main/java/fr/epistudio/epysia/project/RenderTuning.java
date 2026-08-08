package fr.epistudio.epysia.project;

public record RenderTuning(
        boolean gpuCulling,
        boolean sceneIndex,
        boolean multiDraw,
        boolean instancing,
        boolean pipelineMemo,
        boolean cachedTransformLookup,
        boolean sharedMaterialDigest,
        boolean skinOnce,
        boolean animationCulling,
        float animationFullRateDistance,
        boolean frontToBackOpaque,
        boolean shadowLayerReuse,
        boolean ringInstanceBuffers,
        boolean ringObjectUniforms,
        boolean parallelAnimation
) {
    public static final float DEFAULT_ANIMATION_FULL_RATE_DISTANCE = 15.0f;

    public static RenderTuning defaults() {
        return new RenderTuning(false, true, true, true, true, true, true, true, true,
                DEFAULT_ANIMATION_FULL_RATE_DISTANCE, true, false, false, false, true);
    }
}
