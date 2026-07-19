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
import fr.epistudio.epysia.render.backend.PassClear;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CascadedShadowMaps {

    static final int CASCADE_COUNT = 3;
    private static final int SHADOW_MAP_SIZE = 2048;
    private static final float SPLIT_LAMBDA = 0.75f;
    private static final String SHADOW_VERTEX_PATH = "shadow.vert.glsl";
    private static final String SHADOW_FRAGMENT_PATH = "shadow.frag.glsl";
    private static final String MASKED_VERTEX_PATH = "shadow_masked.vert.glsl";
    private static final String MASKED_FRAGMENT_PATH = "shadow_masked.frag.glsl";
    private static final RenderState MASKED_STATE = new RenderState(
            RenderState.OPAQUE_3D.topology(),
            RenderState.OPAQUE_3D.depthTest(),
            RenderState.OPAQUE_3D.blendMode(),
            CullMode.NONE
    );

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;

    private final Matrix4f[] cascadeMatrices = createMatrices();
    private final float[] cascadeSplits = new float[CASCADE_COUNT];
    private final float[] cascadeTexelSizes = new float[CASCADE_COUNT];
    private final List<DrawCommand> casters = new ArrayList<>(256);
    private final ByteBuffer cascadeScratch = BufferUtils.createByteBuffer(MeshShaderBindings.CASCADE_UBO_SIZE);

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
    private boolean cascadesActive;

    CascadedShadowMaps(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        bindingLayout = buildBindingLayout();
        maskedBindingLayout = buildMaskedBindingLayout();
        texture = backend.createTexture(TextureDescriptor.depthArray(
                SHADOW_MAP_SIZE, CASCADE_COUNT, TextureUsage.SAMPLED_DEPTH_SHADOW));
        createCascadeTargets();
        cascadeUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.CASCADE_UBO_SIZE)));
        pipeline = backend.createPipeline(buildShadowPipelineDescriptor());
        maskedPipeline = backend.createPipeline(buildMaskedPipelineDescriptor());
        registerHotReload();
    }

    private static BindingSetLayout buildBindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.CASCADE_UBO_BINDING, BindingType.UNIFORM_BUFFER)
        ));
    }

    private static BindingSetLayout buildMaskedBindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
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

    BindingSetLayout bindingLayout() {
        return bindingLayout;
    }

    BindingSetLayout maskedBindingLayout() {
        return maskedBindingLayout;
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

    void beginFrame() {
        casters.clear();
        cascadesActive = false;
    }

    void submitCaster(DrawCommand command) {
        casters.add(command);
    }

    void update(Camera3D camera, Vector3f lightTravelDirection, float maxShadowDistance, float alpha) {
        float nearPlane = camera.nearPlane();
        float cameraFar = camera.farPlane();
        float shadowFar = Math.min(cameraFar, Math.max(maxShadowDistance, nearPlane + 0.01f));
        computeSplits(nearPlane, shadowFar);
        computeFrustumCorners(camera, alpha);
        float previousSplit = nearPlane;
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            fitCascade(cascade, previousSplit, cascadeSplits[cascade], nearPlane, cameraFar, lightTravelDirection);
            previousSplit = cascadeSplits[cascade];
        }
        cascadesActive = true;
    }

    private void computeSplits(float nearPlane, float farPlane) {
        for (int i = 1; i <= CASCADE_COUNT; i++) {
            float fraction = i / (float) CASCADE_COUNT;
            float uniformSplit = nearPlane + (farPlane - nearPlane) * fraction;
            float logarithmicSplit = (float) (nearPlane * Math.pow(farPlane / nearPlane, fraction));
            cascadeSplits[i - 1] = SPLIT_LAMBDA * logarithmicSplit + (1.0f - SPLIT_LAMBDA) * uniformSplit;
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
        buildCascadeMatrix(cascade, radius, lightTravelDirection);
        cascadeTexelSizes[cascade] = 2.0f * radius / SHADOW_MAP_SIZE;
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
        backend.beginProfileSection("SHADOW_CASCADES");
        for (int cascade = 0; cascade < CASCADE_COUNT; cascade++) {
            writeCascadeIndex(cascade);
            backend.beginPass(cascadeTargets[cascade], PassClear.depthOnly());
            for (int i = 0; i < casters.size(); i++) {
                backend.execute(casters.get(i));
            }
            backend.endPass();
        }
        backend.endProfileSection();
    }

    private void writeCascadeIndex(int cascade) {
        cascadeScratch.clear();
        cascadeScratch.putInt(cascade).putInt(0).putInt(0).putInt(0);
        cascadeScratch.flip();
        backend.writeBuffer(cascadeUbo, cascadeScratch, 0L);
    }

    private PipelineDescriptor buildShadowPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexLayout layout = new VertexLayout(List.of(position), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadShaderSource(), layout, RenderState.OPAQUE_3D, bindingLayout);
    }

    private PipelineDescriptor buildMaskedPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexLayout layout = new VertexLayout(List.of(position, uv), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadMaskedShaderSource(), layout, MASKED_STATE, maskedBindingLayout);
    }

    private ShaderSource loadShaderSource() {
        LoadedShader vertex = shaderLoader.load(SHADOW_VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(SHADOW_FRAGMENT_PATH);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private ShaderSource loadMaskedShaderSource() {
        LoadedShader vertex = shaderLoader.load(MASKED_VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(MASKED_FRAGMENT_PATH);
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
            logger.info("Reloaded shadow cascade pipelines");
        } catch (EpysiaException exception) {
            logger.error("Shadow shader reload failed, keeping previous program", exception);
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
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
