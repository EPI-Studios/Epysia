package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CascadedShadowMaps {

    static final int CASCADE_COUNT = Integer.getInteger("epysia.shadow.cascades", 3);
    private static final int SHADOW_MAP_SIZE = Integer.getInteger("epysia.shadow.size", 1024);
    private static final float SPLIT_LAMBDA = 0.75f;
    private static final String SHADOW_VERTEX_PATH = "shadow.vert.glsl";
    private static final String SHADOW_FRAGMENT_PATH = "shadow.frag.glsl";
    private static final String MASKED_VERTEX_PATH = "shadow_masked.vert.glsl";
    private static final String MASKED_FRAGMENT_PATH = "shadow_masked.frag.glsl";
    private static final RenderState CASTER_STATE = RenderState.OPAQUE_3D.withDepthClamp();
    private static final RenderState MASKED_STATE = new RenderState(
            RenderState.OPAQUE_3D.topology(),
            RenderState.OPAQUE_3D.depthTest(),
            RenderState.OPAQUE_3D.blendMode(),
            CullMode.NONE
    ).withDepthClamp();

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;
    private final SurfaceShadowVariants surfaceVariants;
    private final SurfaceShadowVariants maskedSurfaceVariants;

    private final Matrix4f[] cascadeMatrices = createMatrices();
    private static final float CASTER_CULL_LIGHT_EXTENSION = 64.0f;
    private final Matrix4f[] cascadeCullMatrices = createMatrices();
    private final FrustumIntersection[] cascadeFrusta = createCascadeFrusta();
    private final float[] cascadeSplits = new float[CASCADE_COUNT];
    private final float[] cascadeTexelSizes = new float[CASCADE_COUNT];
    private final ShadowCasterSet casters = new ShadowCasterSet();
    private final ShadowSplitRenderer splitRenderer;
    private final ShadowStatistics statistics;
    private long sceneModificationCount;
    private static final int[] CASCADE_UPDATE_CADENCE = {1, 2, 4};
    private static final float CASCADE_REFIT_DRIFT_FRACTION = 0.25f;
    private final Vector3f[] frozenCenters = createCenters();
    private final float[] frozenRadii = new float[CASCADE_COUNT];
    private final Vector3f frozenLightDirection = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private long updateFrameCounter;
    private boolean farCascadeRefitUsedThisFrame;

    private static Vector3f[] createCenters() {
        Vector3f[] centers = new Vector3f[CASCADE_COUNT];
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            centers[cascade] = new Vector3f();
        }
        return centers;
    }

    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private final Matrix4f scratchLightView = new Matrix4f();
    private final Matrix4f scratchSnap = new Matrix4f();
    private final Vector3f[] nearCorners = createCornerVectors();
    private final Vector3f[] farCorners = createCornerVectors();
    private final Vector3f scratchCorner = new Vector3f();
    private final Vector3f scratchCenter = new Vector3f();
    private final Vector3f scratchEye = new Vector3f();
    private final Vector3f scratchUp = new Vector3f();
    private final Vector4f scratchOrigin = new Vector4f();

    private RenderBackend backend;
    private TextureHandle texture;
    private RenderTargetHandle[] cascadeTargets;
    private PipelineHandle pipeline;
    private PipelineHandle maskedPipeline;
    private BufferHandle cascadeUbo;
    private BindingSetLayout bindingLayout;
    private BindingSetLayout maskedBindingLayout;
    private BindingSetLayout skinnedBindingLayout;
    private BindingSetLayout maskedSkinnedBindingLayout;
    private boolean cascadesActive;

    CascadedShadowMaps(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger,
                       ShadowStatistics statistics, Runnable pipelineInvalidation) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
        this.statistics = statistics;
        this.splitRenderer = new ShadowSplitRenderer(statistics, SHADOW_MAP_SIZE, CASCADE_COUNT);
        this.splitRenderer.setLayerFilter(this::casterVisibleInCascade);
        this.surfaceVariants = new SurfaceShadowVariants(shaderLoader, shaderWatcher, logger,
                SHADOW_VERTEX_PATH, SHADOW_FRAGMENT_PATH, CASTER_STATE, pipelineInvalidation);
        this.maskedSurfaceVariants = new SurfaceShadowVariants(shaderLoader, shaderWatcher, logger,
                MASKED_VERTEX_PATH, MASKED_FRAGMENT_PATH, MASKED_STATE, pipelineInvalidation);
    }

    void setCachingEnabled(boolean enabled) {
        splitRenderer.setCachingEnabled(enabled);
    }

    void setSplitEnabled(boolean enabled) {
        casters.setSplitEnabled(enabled);
        splitRenderer.invalidateAll();
    }

    void invalidateCache() {
        splitRenderer.invalidateAll();
        casters.requestRebuild();
    }

    long staticVideoMemoryBytes() {
        return splitRenderer.staticVideoMemoryBytes();
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        bindingLayout = buildBindingLayout();
        maskedBindingLayout = buildMaskedBindingLayout();
        skinnedBindingLayout = SurfaceShadowVariants.withJointPalette(bindingLayout);
        maskedSkinnedBindingLayout = SurfaceShadowVariants.withJointPalette(maskedBindingLayout);
        texture = backend.createTexture(TextureDescriptor.depthArray(
                SHADOW_MAP_SIZE, CASCADE_COUNT, TextureUsage.SAMPLED_DEPTH_SHADOW));
        createCascadeTargets();
        splitRenderer.initialize(backend, texture, cascadeTargets);
        cascadeUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, buildLayerIndexSlices()));
        pipeline = backend.createPipeline(buildShadowPipelineDescriptor());
        maskedPipeline = backend.createPipeline(buildMaskedPipelineDescriptor());
        surfaceVariants.initialize(backend, bindingLayout, pipeline);
        maskedSurfaceVariants.initialize(backend, maskedBindingLayout, maskedPipeline);
        registerHotReload();
    }

    private static BindingSetLayout buildBindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(MeshShaderBindings.CASCADE_UBO_BINDING, BindingType.UNIFORM_BUFFER)
        ));
    }

    private static BindingSetLayout buildMaskedBindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(MeshShaderBindings.CASCADE_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.SHADOW_MASK_MATERIAL_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.SHADOW_MASK_ALBEDO_BINDING, BindingType.SAMPLED_TEXTURE_2D)
        ));
    }

    private void createCascadeTargets() {
        cascadeTargets = new RenderTargetHandle[CASCADE_COUNT];
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            cascadeTargets[cascade] = backend.createRenderTarget(
                    RenderTargetDescriptor.depthArrayLayer(SHADOW_MAP_SIZE, texture, cascade));
        }
    }

    TextureHandle texture() {
        return texture;
    }

    PipelineHandle pipeline() {
        return pipeline;
    }

    PipelineHandle maskedPipeline() {
        return maskedPipeline;
    }

    PipelineHandle pipelineFor(String surfacePath, boolean masked, boolean frozenTime, boolean skinned) {
        SurfaceShadowVariants variants = masked ? maskedSurfaceVariants : surfaceVariants;
        return variants.pipelineFor(surfacePath, frozenTime, skinned);
    }

    BindingSetLayout bindingLayout() {
        return bindingLayout;
    }

    BindingSetLayout maskedBindingLayout() {
        return maskedBindingLayout;
    }

    BindingSetLayout skinnedBindingLayout() {
        return skinnedBindingLayout;
    }

    BindingSetLayout maskedSkinnedBindingLayout() {
        return maskedSkinnedBindingLayout;
    }


    BufferHandle cascadeUbo() {
        return cascadeUbo;
    }

    boolean cascadesActive() {
        return cascadesActive;
    }

    int activeCascadeCount() {
        return cascadesActive ? CASCADE_COUNT : 0;
    }

    Matrix4f cascadeMatrix(int cascade) {
        return cascadeMatrices[cascade];
    }

    float cascadeSplit(int cascade) {
        return cascadeSplits[cascade];
    }

    float cascadeTexelSize(int cascade) {
        return cascadeTexelSizes[cascade];
    }

    void beginFrame(long sceneModificationCount) {
        casters.beginFrame();
        this.sceneModificationCount = sceneModificationCount;
        cascadesActive = false;
    }

    void submitCaster(DrawCommand command, long casterIdentity, long casterSignature, boolean casterTimeAnimated) {
        casters.submit(command, casterIdentity, casterSignature, casterTimeAnimated);
    }

    void submitCaster(DrawCommand command, long casterIdentity, long casterSignature,
                      boolean casterTimeAnimated, Vector3f worldMin, Vector3f worldMax) {
        casters.submit(command, casterIdentity, casterSignature, casterTimeAnimated, worldMin, worldMax);
    }

    void update(Camera3D camera, Vector3f lightTravelDirection, float maxShadowDistance, float alpha) {
        float nearPlane = camera.nearPlane();
        float cameraFar = camera.farPlane();
        float shadowFar = Math.min(cameraFar, Math.max(maxShadowDistance, nearPlane + 0.01f));
        computeSplits(nearPlane, shadowFar, camera.orthographic());
        computeFrustumCorners(camera, alpha);
        updateFrameCounter++;
        farCascadeRefitUsedThisFrame = false;
        float previousSplit = nearPlane;
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            fitCascade(cascade, previousSplit, cascadeSplits[cascade], nearPlane, cameraFar, lightTravelDirection);
            previousSplit = cascadeSplits[cascade];
        }
        frozenLightDirection.set(lightTravelDirection);
        cascadesActive = true;
    }

    private void computeSplits(float nearPlane, float farPlane, boolean orthographic) {
        float lambda = orthographic ? 0.0f : SPLIT_LAMBDA;
        for (int i = 1; i <= CASCADE_COUNT; i++) {
            float fraction = i / (float) CASCADE_COUNT;
            float uniformSplit = nearPlane + (farPlane - nearPlane) * fraction;
            float logarithmicSplit = (float) (nearPlane * Math.pow(farPlane / nearPlane, fraction));
            cascadeSplits[i - 1] = lambda * logarithmicSplit + (1.0f - lambda) * uniformSplit;
        }
    }

    private void computeFrustumCorners(Camera3D camera, float alpha) {
        camera.viewProjection(alpha).invert(scratchInverseViewProjection);
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? -1.0f : 1.0f;
            float y = (i & 2) == 0 ? -1.0f : 1.0f;
            scratchInverseViewProjection.transformProject(nearCorners[i].set(x, y, -1.0f));
            scratchInverseViewProjection.transformProject(farCorners[i].set(x, y, 1.0f));
        }
    }

    private void fitCascade(int cascade, float sliceNear, float sliceFar,
                            float cameraNear, float cameraFar, Vector3f lightTravelDirection) {
        float range = Math.max(cameraFar - cameraNear, 1.0e-4f);
        float tNear = (sliceNear - cameraNear) / range;
        float tFar = (sliceFar - cameraNear) / range;
        scratchCenter.zero();
        for (int i = 0; i < 4; i++) {
            scratchCenter.add(sliceCorner(i, tNear));
            scratchCenter.add(sliceCorner(i, tFar));
        }
        scratchCenter.mul(1.0f / 8.0f);
        float radius = sliceBoundingRadius(tNear, tFar);
        if (!shouldRefitCascade(cascade, radius, lightTravelDirection)) {
            return;
        }
        buildCascadeMatrix(cascade, radius, lightTravelDirection);
        cascadeTexelSizes[cascade] = 2.0f * radius / SHADOW_MAP_SIZE;
        frozenCenters[cascade].set(scratchCenter);
        frozenRadii[cascade] = radius;
    }

    private boolean shouldRefitCascade(int cascade, float radius, Vector3f lightTravelDirection) {
        if (cascade == 0 || frozenRadii[cascade] == 0.0f) {
            return true;
        }
        if (!lightTravelDirection.equals(frozenLightDirection)) {
            return true;
        }
        if (Math.abs(radius - frozenRadii[cascade]) > radius * 0.01f) {
            return consumeFarCascadeRefit();
        }
        int cadence = CASCADE_UPDATE_CADENCE[Math.min(cascade, CASCADE_UPDATE_CADENCE.length - 1)];
        if ((updateFrameCounter + cascade) % cadence == 0) {
            return consumeFarCascadeRefit();
        }
        if (frozenCenters[cascade].distance(scratchCenter) > radius * CASCADE_REFIT_DRIFT_FRACTION) {
            return consumeFarCascadeRefit();
        }
        return false;
    }

    private boolean consumeFarCascadeRefit() {
        if (farCascadeRefitUsedThisFrame) {
            return false;
        }
        farCascadeRefitUsedThisFrame = true;
        return true;
    }

    private Vector3f sliceCorner(int index, float t) {
        return scratchCorner.set(nearCorners[index]).lerp(farCorners[index], t);
    }

    private float sliceBoundingRadius(float tNear, float tFar) {
        float radiusSquared = 0.0f;
        for (int i = 0; i < 4; i++) {
            radiusSquared = Math.max(radiusSquared, sliceCorner(i, tNear).distanceSquared(scratchCenter));
            radiusSquared = Math.max(radiusSquared, sliceCorner(i, tFar).distanceSquared(scratchCenter));
        }
        float radius = (float) Math.sqrt(radiusSquared);
        return (float) Math.ceil(radius * 16.0f) / 16.0f;
    }

    private void buildCascadeMatrix(int cascade, float radius, Vector3f lightTravelDirection) {
        scratchEye.set(lightTravelDirection).negate().mul(radius * 2.0f).add(scratchCenter);
        scratchLightView.setLookAt(scratchEye, scratchCenter, chooseUp(lightTravelDirection));
        Matrix4f matrix = cascadeMatrices[cascade];
        matrix.setOrtho(-radius, radius, -radius, radius, 0.0f, radius * 4.0f);
        matrix.mul(scratchLightView);
        snapToTexelGrid(matrix);
        buildCascadeCullMatrix(cascade, radius);
    }

    private void buildCascadeCullMatrix(int cascade, float radius) {
        Matrix4f cullMatrix = cascadeCullMatrices[cascade];
        cullMatrix.setOrtho(-radius, radius, -radius, radius,
                -radius * CASTER_CULL_LIGHT_EXTENSION, radius * 4.0f);
        cullMatrix.mul(scratchLightView);
    }

    private void snapToTexelGrid(Matrix4f matrix) {
        scratchOrigin.set(0.0f, 0.0f, 0.0f, 1.0f);
        matrix.transform(scratchOrigin);
        float halfSize = SHADOW_MAP_SIZE * 0.5f;
        float snappedX = Math.round(scratchOrigin.x * halfSize) / halfSize;
        float snappedY = Math.round(scratchOrigin.y * halfSize) / halfSize;
        scratchSnap.translation(snappedX - scratchOrigin.x, snappedY - scratchOrigin.y, 0.0f);
        scratchSnap.mul(matrix, matrix);
    }

    private Vector3f chooseUp(Vector3f lightTravelDirection) {
        if (Math.abs(lightTravelDirection.y) > 0.99f) {
            return scratchUp.set(0.0f, 0.0f, 1.0f);
        }
        return scratchUp.set(0.0f, 1.0f, 0.0f);
    }

    void render() {
        if (!cascadesActive || casters.isEmpty()) {
            return;
        }
        statistics.recordCasters(casters.submittedCount());
        casters.classify();
        refreshCascadeFrusta();
        backend.beginProfileSection("SHADOW_CASCADES");
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            splitRenderer.renderTarget(cascade, cascadeViewSignature(cascade), casters, this::writeCascadeIndex);
        }
        backend.clearUniformSlotOverride();
        backend.endProfileSection();
    }

    private static FrustumIntersection[] createCascadeFrusta() {
        FrustumIntersection[] frusta = new FrustumIntersection[CASCADE_COUNT];
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            frusta[cascade] = new FrustumIntersection();
        }
        return frusta;
    }

    private void refreshCascadeFrusta() {
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            cascadeFrusta[cascade].set(cascadeCullMatrices[cascade]);
        }
    }

    private boolean casterVisibleInCascade(int cascade, ShadowCaster caster) {
        if (!caster.bounded()) {
            return true;
        }
        return cascadeFrusta[cascade].testAab(
                caster.minX(), caster.minY(), caster.minZ(),
                caster.maxX(), caster.maxY(), caster.maxZ());
    }

    private long cascadeViewSignature(int cascade) {
        long signature = ShadowSignatures.mix(ShadowSignatures.seed(), sceneModificationCount);
        return ShadowSignatures.mixMatrix(signature, cascadeMatrices[cascade]);
    }

    private static ByteBuffer buildLayerIndexSlices() {
        ByteBuffer slices = BufferUtils.createByteBuffer(
                MeshShaderBindings.SHADOW_LAYER_INDEX_COUNT * MeshShaderBindings.SHADOW_LAYER_INDEX_STRIDE);
        for (int layer = 0; layer < MeshShaderBindings.SHADOW_LAYER_INDEX_COUNT; layer++) {
            slices.position(layer * MeshShaderBindings.SHADOW_LAYER_INDEX_STRIDE);
            slices.putInt(layer).putInt(0).putInt(0).putInt(0);
        }
        slices.clear();
        return slices;
    }

    private void writeCascadeIndex(int cascade) {
        backend.setUniformSlotOverride(MeshShaderBindings.CASCADE_UBO_BINDING, cascadeUbo,
                (long) cascade * MeshShaderBindings.SHADOW_LAYER_INDEX_STRIDE, MeshShaderBindings.CASCADE_UBO_SIZE);
    }

    private PipelineDescriptor buildShadowPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexLayout layout = new VertexLayout(List.of(position), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadShaderSource(), layout, CASTER_STATE, bindingLayout);
    }

    private PipelineDescriptor buildMaskedPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexLayout layout = new VertexLayout(List.of(position, uv), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadMaskedShaderSource(), layout, MASKED_STATE, maskedBindingLayout);
    }

    private ShaderSource loadShaderSource() {
        return shadowShaderSource(SHADOW_VERTEX_PATH, SHADOW_FRAGMENT_PATH);
    }

    private ShaderSource loadMaskedShaderSource() {
        return shadowShaderSource(MASKED_VERTEX_PATH, MASKED_FRAGMENT_PATH);
    }

    private ShaderSource shadowShaderSource(String vertexPath, String fragmentPath) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private void registerHotReload() {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(shaderLoader.load(SHADOW_VERTEX_PATH).dependencyPaths());
        dependencies.addAll(shaderLoader.load(SHADOW_FRAGMENT_PATH).dependencyPaths());
        dependencies.addAll(shaderLoader.load(MASKED_VERTEX_PATH).dependencyPaths());
        dependencies.addAll(shaderLoader.load(MASKED_FRAGMENT_PATH).dependencyPaths());
        shaderWatcher.watch(List.copyOf(dependencies), this::reload);
    }

    private void reload() {
        try {
            backend.updatePipelineShaders(pipeline, loadShaderSource());
            backend.updatePipelineShaders(maskedPipeline, loadMaskedShaderSource());
            invalidateCache();
            logger.info("Reloaded shadow cascade pipelines");
        } catch (EpysiaException exception) {
            logger.error("Shadow shader reload failed, keeping previous program", exception);
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        surfaceVariants.shutdown();
        maskedSurfaceVariants.shutdown();
        splitRenderer.shutdown();
        if (pipeline != null) backend.destroy(pipeline);
        if (maskedPipeline != null) backend.destroy(maskedPipeline);
        if (cascadeTargets != null) {
            for (RenderTargetHandle target : cascadeTargets) {
                backend.destroy(target);
            }
        }
        if (texture != null) backend.destroy(texture);
        if (cascadeUbo != null) backend.destroy(cascadeUbo);
        pipeline = null;
        maskedPipeline = null;
        cascadeTargets = null;
        texture = null;
        cascadeUbo = null;
    }

    private static Matrix4f[] createMatrices() {
        Matrix4f[] matrices = new Matrix4f[CASCADE_COUNT];
        for (int i = 0; i < CASCADE_COUNT; i++) {
            matrices[i] = new Matrix4f();
        }
        return matrices;
    }

    private static Vector3f[] createCornerVectors() {
        Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            corners[i] = new Vector3f();
        }
        return corners;
    }
}
