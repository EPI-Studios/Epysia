package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.FullscreenQuad;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

public final class PostProcessSystem implements RenderSystem {

    private static final String DEFAULT_VERTEX_PATH = "post.vert.glsl";
    private static final String DEFAULT_FRAGMENT_PATH = "post.frag.glsl";
    private static final String FXAA_FRAGMENT_PATH = "postfx/fxaa.frag.glsl";
    private static final int UBO_SIZE = 176;
    private static final float DEFAULT_NEAR_PLANE = 0.1f;
    private static final float DEFAULT_FAR_PLANE = 100.0f;
    private static final RenderState PASS_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final RenderSurface window;
    private final Logger logger;
    private final String vertexPath;
    private final String fragmentPath;
    private final PostProcessSettings settings = new PostProcessSettings();
    private final FullscreenQuad quad = new FullscreenQuad();
    private final SsaoPass ssao;
    private final BloomChain bloom;
    private final PostEffectChain effectChain;
    private final Vector3f appliedClearColor = Scene.defaultClearColor();
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);

    private RenderBackend backend;
    private StageConfigurer stageConfigurer;
    private TextureHandle sceneColorTexture;
    private TextureHandle sceneDepthTexture;
    private RenderTargetHandle sceneTarget;
    private TextureHandle ldrColorTexture;
    private RenderTargetHandle ldrTarget;
    private PipelineHandle tonemapPipeline;
    private PipelineHandle antiAliasPipeline;
    private BufferHandle postUbo;
    private BindingSetHandle tonemapBindings;
    private BindingSetHandle antiAliasBindings;
    private TextureHandle currentTonemapInput;
    private TextureHandle currentAntiAliasInput;
    private Camera3D activeCamera;
    private int targetWidth;
    private int targetHeight;

    public PostProcessSystem(ShaderLoader shaderLoader, RenderSurface window, Logger logger) {
        this(shaderLoader, window, logger, DEFAULT_VERTEX_PATH, DEFAULT_FRAGMENT_PATH);
    }

    public PostProcessSystem(ShaderLoader shaderLoader, RenderSurface window, Logger logger,
                             String vertexPath, String fragmentPath) {
        this.shaderLoader = shaderLoader;
        this.window = window;
        this.logger = logger;
        this.vertexPath = vertexPath;
        this.fragmentPath = fragmentPath;
        this.ssao = new SsaoPass(shaderLoader, quad, settings);
        this.bloom = new BloomChain(shaderLoader, quad);
        this.effectChain = new PostEffectChain(shaderLoader, quad, logger);
    }

    public PostProcessSettings settings() {
        return settings;
    }

    public void setShaderWatcher(ShaderWatcher watcher) {
        effectChain.setShaderWatcher(watcher);
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.stageConfigurer = configurer;
        int width = Math.max(1, window.framebufferWidth());
        int height = Math.max(1, window.framebufferHeight());
        targetWidth = width;
        targetHeight = height;
        quad.initialize(backend);
        createTargets(width, height);
        createPostUbo();
        tonemapPipeline = backend.createPipeline(buildPipelineDescriptor(vertexPath, fragmentPath, tonemapLayout()));
        antiAliasPipeline = backend.createPipeline(buildPipelineDescriptor(DEFAULT_VERTEX_PATH, FXAA_FRAGMENT_PATH, antiAliasLayout()));
        ssao.initialize(backend, sceneDepthTexture, width, height);
        bloom.initialize(backend, sceneColorTexture, width, height);
        effectChain.initialize(backend);
        effectChain.configure(sceneColorTexture, sceneDepthTexture, ldrColorTexture, width, height);
        createBindings();
        bindStageTargets();
        configurer.bindStagePreparation(RenderPasses.POST, this::runPostPasses);
    }

    @Override
    public void onResize(RenderBackend backend, StageConfigurer configurer, int width, int height) {
        this.stageConfigurer = configurer;
        targetWidth = Math.max(1, width);
        targetHeight = Math.max(1, height);
        backend.destroy(tonemapBindings);
        backend.destroy(antiAliasBindings);
        destroyTargets();
        createTargets(targetWidth, targetHeight);
        ssao.onResize(sceneDepthTexture, targetWidth, targetHeight);
        bloom.onResize(sceneColorTexture, targetWidth, targetHeight);
        effectChain.configure(sceneColorTexture, sceneDepthTexture, ldrColorTexture, targetWidth, targetHeight);
        createBindings();
        bindStageTargets();
    }

    public TextureHandle sceneDepthTexture() {
        return sceneDepthTexture;
    }

    public RenderTargetHandle sceneTarget() {
        return sceneTarget;
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        activeCamera = context.primaryCamera().orElse(null);
        refreshSceneClearColor(scene);
        refreshSsaoResolutionMode();
        writeUbo(activeCamera, context.interpolationAlpha());
        ssao.writeUbo(activeCamera, settings, context.interpolationAlpha());
        bloom.writeUbo(settings);
        updateEffectChainCameraState(activeCamera, context.interpolationAlpha());
        effectChain.prepare(resolveStack(scene));
        refreshEffectChainBindings();
        frame.submit(RenderPasses.POST, DrawCommand.of(antiAliasPipeline, quad.mesh(), antiAliasBindings));
    }

    private void updateEffectChainCameraState(Camera3D camera, float alpha) {
        if (camera == null) {
            effectChain.setCameraState(DEFAULT_NEAR_PLANE, DEFAULT_FAR_PLANE,
                    scratchCameraPosition.zero(), scratchInverseViewProjection.identity());
            return;
        }
        camera.position(scratchCameraPosition, alpha);
        camera.viewProjection(alpha).invert(scratchInverseViewProjection);
        effectChain.setCameraState(camera.nearPlane(), camera.farPlane(),
                scratchCameraPosition, scratchInverseViewProjection);
    }

    private PostEffectStack resolveStack(Scene scene) {
        if (activeCamera != null && activeCamera.postEffectStack().isPresent()) {
            return activeCamera.postEffectStack().get();
        }
        return scene.postEffects();
    }

    private void refreshEffectChainBindings() {
        TextureHandle tonemapInput = effectChain.outputTexture(PostEffectInsertionPoint.BEFORE_TONEMAP);
        TextureHandle antiAliasInput = effectChain.outputTexture(PostEffectInsertionPoint.AFTER_TONEMAP);
        if (tonemapInput.equals(currentTonemapInput) && antiAliasInput.equals(currentAntiAliasInput)) {
            return;
        }
        backend.destroy(tonemapBindings);
        backend.destroy(antiAliasBindings);
        createBindings();
    }

    private void refreshSceneClearColor(Scene scene) {
        if (appliedClearColor.equals(scene.clearColor())) {
            return;
        }
        appliedClearColor.set(scene.clearColor());
        bindStageTargets();
    }

    private void refreshSsaoResolutionMode() {
        if (ssao.matchesResolutionMode()) {
            return;
        }
        ssao.onResize(sceneDepthTexture, targetWidth, targetHeight);
        backend.destroy(tonemapBindings);
        backend.destroy(antiAliasBindings);
        createBindings();
    }

    private void runPostPasses() {
        if (settings.ambientOcclusionEnabled() && activeCamera != null) {
            ssao.render();
        }
        if (settings.bloomEnabled()) {
            bloom.render();
        }
        effectChain.render(PostEffectInsertionPoint.BEFORE_TONEMAP);
        backend.beginPass(ldrTarget, PassClear.none());
        backend.execute(DrawCommand.of(tonemapPipeline, quad.mesh(), tonemapBindings));
        backend.endPass();
        effectChain.render(PostEffectInsertionPoint.AFTER_TONEMAP);
    }

    private void createTargets(int width, int height) {
        sceneColorTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.RGBA16F, TextureUsage.SAMPLED));
        sceneDepthTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
        sceneTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                width, height, List.of(sceneColorTexture), Optional.of(sceneDepthTexture)));
        ldrColorTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.RGBA8, TextureUsage.SAMPLED));
        ldrTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                width, height, List.of(ldrColorTexture), Optional.empty()));
    }

    private void destroyTargets() {
        backend.destroy(sceneTarget);
        backend.destroy(sceneColorTexture);
        backend.destroy(sceneDepthTexture);
        backend.destroy(ldrTarget);
        backend.destroy(ldrColorTexture);
    }

    private BindingSetLayout tonemapLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(3, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(4, BindingType.SAMPLED_TEXTURE_2D)
        ));
    }

    private BindingSetLayout antiAliasLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER)
        ));
    }

    private void createBindings() {
        currentTonemapInput = effectChain.outputTexture(PostEffectInsertionPoint.BEFORE_TONEMAP);
        currentAntiAliasInput = effectChain.outputTexture(PostEffectInsertionPoint.AFTER_TONEMAP);
        tonemapBindings = backend.createBindingSet(new BindingSetDescriptor(tonemapLayout(), List.of(
                new Binding(0, new SampledTextureBinding(currentTonemapInput)),
                new Binding(1, UniformBufferBinding.whole(postUbo, UBO_SIZE)),
                new Binding(2, new SampledTextureBinding(sceneDepthTexture)),
                new Binding(3, new SampledTextureBinding(bloom.bloomTexture())),
                new Binding(4, new SampledTextureBinding(ssao.occlusionTexture()))
        )));
        antiAliasBindings = backend.createBindingSet(new BindingSetDescriptor(antiAliasLayout(), List.of(
                new Binding(0, new SampledTextureBinding(currentAntiAliasInput)),
                new Binding(1, UniformBufferBinding.whole(postUbo, UBO_SIZE))
        )));
    }

    private PipelineDescriptor buildPipelineDescriptor(String vertex, String fragment, BindingSetLayout layout) {
        ShaderSource source = new ShaderSource(
                shaderLoader.load(vertex).source(),
                shaderLoader.load(fragment).source());
        return new PipelineDescriptor(source, FullscreenQuad.LAYOUT, PASS_STATE, layout);
    }

    private void createPostUbo() {
        ByteBuffer initial = BufferUtils.createByteBuffer(UBO_SIZE);
        postUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    private void bindStageTargets() {
        PassClear sceneClear = PassClear.color(appliedClearColor.x, appliedClearColor.y, appliedClearColor.z);
        PassClear sceneNoClear = PassClear.none();
        stageConfigurer.bindStageTarget(RenderPasses.OPAQUE_3D, sceneTarget, sceneClear);
        stageConfigurer.bindStageTarget(RenderPasses.TRANSPARENT_3D, sceneTarget, sceneNoClear);
        stageConfigurer.bindStageTarget(RenderPasses.WORLD_2D, sceneTarget, sceneNoClear);
        stageConfigurer.bindStageTarget(RenderPasses.POST, RenderTargetHandle.SCREEN, PassClear.color(0.0f, 0.0f, 0.0f));
    }

    private void writeUbo(Camera3D camera, float alpha) {
        uboScratch.clear();
        uboScratch.putFloat(settings.vignetteStrength()).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(settings.gradeGamma()).putFloat(settings.gradeExposure()).putFloat(0.0f).putFloat(0.0f);
        writeFogParameters(camera, alpha);
        writeInverseViewProjection(camera, alpha);
        writeEffectParameters(camera);
        uboScratch.flip();
        backend.writeBuffer(postUbo, uboScratch, 0L);
    }

    private void writeEffectParameters(Camera3D camera) {
        float occlusionStrength = (settings.ambientOcclusionEnabled() && camera != null) ? 1.0f : 0.0f;
        float bloomIntensity = settings.bloomEnabled() ? settings.bloomIntensity() : 0.0f;
        float antiAlias = settings.antiAliasingEnabled() ? 1.0f : 0.0f;
        uboScratch.putFloat(occlusionStrength).putFloat(bloomIntensity).putFloat(antiAlias).putFloat(0.0f);
    }

    private void writeFogParameters(Camera3D camera, float alpha) {
        float fogStrength = (settings.fogEnabled() && camera != null) ? 1.0f : 0.0f;
        Vector3f fogColor = settings.fogColor();
        uboScratch.putFloat(fogColor.x).putFloat(fogColor.y).putFloat(fogColor.z).putFloat(fogStrength);
        uboScratch.putFloat(settings.fogDistanceStart()).putFloat(settings.fogDistanceDensity())
                .putFloat(settings.fogHeightOrigin()).putFloat(settings.fogHeightFalloff());
        float nearPlane = camera != null ? camera.nearPlane() : 0.1f;
        float farPlane = camera != null ? camera.farPlane() : 100.0f;
        Vector3f cameraPosition = camera != null
                ? camera.position(scratchCameraPosition, alpha)
                : scratchCameraPosition.set(0.0f);
        uboScratch.putFloat(nearPlane).putFloat(farPlane).putFloat(settings.fogHeightDensity()).putFloat(0.0f);
        uboScratch.putFloat(cameraPosition.x).putFloat(cameraPosition.y).putFloat(cameraPosition.z).putFloat(0.0f);
    }

    private void writeInverseViewProjection(Camera3D camera, float alpha) {
        if (camera != null) {
            camera.viewProjection(alpha).invert(scratchInverseViewProjection);
        } else {
            scratchInverseViewProjection.identity();
        }
        scratchInverseViewProjection.get(uboScratch.position(), uboScratch);
        uboScratch.position(uboScratch.position() + 64);
    }

    @Override
    public void shutdown(RenderBackend backend) {
        backend.destroy(tonemapBindings);
        backend.destroy(antiAliasBindings);
        ssao.shutdown();
        bloom.shutdown();
        effectChain.shutdown();
        backend.destroy(postUbo);
        backend.destroy(tonemapPipeline);
        backend.destroy(antiAliasPipeline);
        destroyTargets();
        quad.shutdown();
    }
}
