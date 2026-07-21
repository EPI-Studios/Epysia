package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferHandle;
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
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class PointShadowAtlas {

    private static final int SHADOW_SIZE = 512;
    private static final int TOTAL_LAYERS = MeshShaderBindings.MAX_SHADOW_POINTS * MeshShaderBindings.POINT_SHADOW_FACES;
    private static final String VERTEX_PATH = "shadow_point.vert.glsl";
    private static final String FRAGMENT_PATH = "shadow.frag.glsl";

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;

    private final Matrix4f[] matrices = createMatrices();
    private final Map<PointLight, Integer> indexByPoint = new IdentityHashMap<>();
    private final ShadowCasterSet casters = new ShadowCasterSet();
    private final ShadowSplitRenderer splitRenderer;
    private final ShadowStatistics statistics;
    private long sceneModificationCount;
    private final ByteBuffer indexScratch = BufferUtils.createByteBuffer(MeshShaderBindings.CASCADE_UBO_SIZE);

    private RenderBackend backend;
    private BufferHandle indexUbo;
    private TextureHandle texture;
    private RenderTargetHandle[] targets;
    private PipelineHandle pipeline;
    private int activeCount;

    private final SurfaceShadowVariants surfaceVariants;

    PointShadowAtlas(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger,
                     ShadowStatistics statistics, Runnable pipelineInvalidation) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
        this.statistics = statistics;
        this.splitRenderer = new ShadowSplitRenderer(statistics, SHADOW_SIZE, TOTAL_LAYERS);
        this.surfaceVariants = new SurfaceShadowVariants(shaderLoader, shaderWatcher, logger,
                VERTEX_PATH, FRAGMENT_PATH, RenderState.OPAQUE_3D, pipelineInvalidation);
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

    void initialize(RenderBackend backend, BufferHandle indexUbo) {
        this.backend = backend;
        this.indexUbo = indexUbo;
        texture = backend.createTexture(TextureDescriptor.depthArray(
                SHADOW_SIZE, TOTAL_LAYERS, TextureUsage.SAMPLED_DEPTH_SHADOW));
        createTargets();
        splitRenderer.initialize(backend, texture, targets);
        pipeline = backend.createPipeline(buildPipelineDescriptor());
        surfaceVariants.initialize(backend, buildBindingLayout(), pipeline);
        registerHotReload();
    }

    private void createTargets() {
        targets = new RenderTargetHandle[TOTAL_LAYERS];
        for (int layer = 0; layer < TOTAL_LAYERS; layer++) {
            targets[layer] = backend.createRenderTarget(
                    RenderTargetDescriptor.depthArrayLayer(SHADOW_SIZE, texture, layer));
        }
    }

    void beginFrame(long sceneModificationCount) {
        casters.beginFrame();
        this.sceneModificationCount = sceneModificationCount;
        indexByPoint.clear();
        activeCount = 0;
    }

    int assign(PointLight point, Matrix4f[] faceMatrices) {
        if (activeCount >= MeshShaderBindings.MAX_SHADOW_POINTS) {
            return -1;
        }
        int base = activeCount++;
        for (int face = 0; face < MeshShaderBindings.POINT_SHADOW_FACES; face++) {
            matrices[base * MeshShaderBindings.POINT_SHADOW_FACES + face].set(faceMatrices[face]);
        }
        indexByPoint.put(point, base);
        return base;
    }

    int indexFor(PointLight point) {
        return indexByPoint.getOrDefault(point, -1);
    }

    Matrix4f matrix(int layer) {
        return matrices[layer];
    }

    int activeCount() {
        return activeCount;
    }

    PipelineHandle pipeline() {
        return pipeline;
    }

    PipelineHandle pipelineFor(String surfacePath, boolean frozenTime) {
        return surfaceVariants.pipelineFor(surfacePath, frozenTime);
    }

    TextureHandle texture() {
        return texture;
    }

    void submitCaster(DrawCommand command, long casterIdentity, long casterSignature, boolean casterTimeAnimated) {
        casters.submit(command, casterIdentity, casterSignature, casterTimeAnimated);
    }

    void render() {
        if (activeCount == 0 || casters.isEmpty()) {
            return;
        }
        statistics.recordCasters(casters.submittedCount());
        casters.classify();
        backend.beginProfileSection("SHADOW_POINTS");
        int layers = activeCount * MeshShaderBindings.POINT_SHADOW_FACES;
        for (int layer = 0; layer < layers; layer++) {
            splitRenderer.renderTarget(layer, layerViewSignature(layer), casters, this::writeIndex);
        }
        backend.endProfileSection();
    }

    private long layerViewSignature(int layer) {
        long signature = ShadowSignatures.mix(ShadowSignatures.seed(), sceneModificationCount);
        return ShadowSignatures.mixMatrix(signature, matrices[layer]);
    }

    private void writeIndex(int layer) {
        indexScratch.clear();
        indexScratch.putInt(layer).putInt(0).putInt(0).putInt(0);
        indexScratch.flip();
        backend.writeBuffer(indexUbo, indexScratch, 0L);
    }

    private static BindingSetLayout buildBindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(MeshShaderBindings.CASCADE_UBO_BINDING, BindingType.UNIFORM_BUFFER)
        ));
    }

    private PipelineDescriptor buildPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexLayout layout = new VertexLayout(List.of(position), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadShaderSource(), layout, RenderState.OPAQUE_3D, buildBindingLayout());
    }

    private ShaderSource loadShaderSource() {
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private void registerHotReload() {
        if (!shaderWatcher.active()) {
            return;
        }
        shaderWatcher.watch(shaderLoader.load(VERTEX_PATH).dependencyPaths(), this::reload);
    }

    private void reload() {
        try {
            backend.updatePipelineShaders(pipeline, loadShaderSource());
            invalidateCache();
            logger.info("Reloaded point shadow pipeline");
        } catch (EpysiaException exception) {
            logger.error("Point shadow shader reload failed, keeping previous program", exception);
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        surfaceVariants.shutdown();
        splitRenderer.shutdown();
        if (pipeline != null) {
            backend.destroy(pipeline);
        }
        if (targets != null) {
            for (RenderTargetHandle target : targets) {
                backend.destroy(target);
            }
        }
        if (texture != null) {
            backend.destroy(texture);
        }
        pipeline = null;
        targets = null;
        texture = null;
    }

    private static Matrix4f[] createMatrices() {
        Matrix4f[] result = new Matrix4f[TOTAL_LAYERS];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Matrix4f();
        }
        return result;
    }
}
