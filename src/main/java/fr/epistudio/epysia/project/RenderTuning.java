package fr.epistudio.epysia.project;

public record RenderTuning(
        boolean gpuCulling,
        int gpuCullMinimumInstances,
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
    public static final int DEFAULT_GPU_CULL_MINIMUM_INSTANCES = 16;
    public static final int MINIMUM_GPU_CULL_INSTANCES = 1;
    public static final int MAXIMUM_GPU_CULL_INSTANCES = 4096;

    public static RenderTuning defaults() {
        return new RenderTuning(false, DEFAULT_GPU_CULL_MINIMUM_INSTANCES,
                true, true, true, true, true, true, true, true,
                DEFAULT_ANIMATION_FULL_RATE_DISTANCE, true, false, false, false, true);
    }
}
