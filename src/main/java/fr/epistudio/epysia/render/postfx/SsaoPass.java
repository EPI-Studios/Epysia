package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.components.Camera3D;
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
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

final class SsaoPass {

    private static final int SSAO_UBO_SIZE = 160;
    private static final float SELF_OCCLUSION_BIAS = 0.025f;
    private static final int HALF_RESOLUTION_DIVISOR = 2;
    private static final RenderState PASS_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final PostProcessSettings settings;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(SSAO_UBO_SIZE);
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();

    private RenderBackend backend;
    private PipelineHandle ssaoPipeline;
    private PipelineHandle horizontalBlurPipeline;
    private PipelineHandle verticalBlurPipeline;
    private BufferHandle ssaoUbo;
    private TextureHandle rawTexture;
    private TextureHandle horizontalTexture;
    private TextureHandle blurredTexture;
    private RenderTargetHandle rawTarget;
    private RenderTargetHandle horizontalTarget;
    private RenderTargetHandle blurredTarget;
    private BindingSetHandle ssaoBindings;
    private BindingSetHandle horizontalBlurBindings;
    private BindingSetHandle verticalBlurBindings;
    private boolean fullResolution;
    private TextureHandle sceneNormal;

    SsaoPass(ShaderLoader shaderLoader, FullscreenQuad quad, PostProcessSettings settings) {
        this.shaderLoader = shaderLoader;
        this.quad = quad;
        this.settings = settings;
    }

    void initialize(RenderBackend backend, TextureHandle sceneDepth, TextureHandle sceneNormal,
                    int width, int height) {
        this.backend = backend;
        ssaoUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(SSAO_UBO_SIZE), true));
        ssaoPipeline = createPipeline("postfx/ssao.frag.glsl", ssaoLayout());
        horizontalBlurPipeline = createPipeline("postfx/ssao_blur_horizontal.frag.glsl", blurLayout());
        verticalBlurPipeline = createPipeline("postfx/ssao_blur_vertical.frag.glsl", blurLayout());
        this.sceneNormal = sceneNormal;
        createTargets(sceneDepth, width, height);
    }

    void onResize(TextureHandle sceneDepth, TextureHandle sceneNormal, int width, int height) {
        this.sceneNormal = sceneNormal;
        destroyTargets();
        createTargets(sceneDepth, width, height);
    }

    boolean matchesResolutionMode() {
        return fullResolution == settings.ambientOcclusionFullResolution();
    }

    TextureHandle occlusionTexture() {
        return blurredTexture;
    }

    void writeUbo(Camera3D camera, PostProcessSettings settings, float alpha) {
        if (camera == null) {
            return;
        }
        uboScratch.clear();
        Matrix4f viewProjection = camera.viewProjection(alpha);
        viewProjection.get(0, uboScratch);
        viewProjection.invert(scratchInverseViewProjection).get(64, uboScratch);
        uboScratch.position(128);
        uboScratch.putFloat(settings.ambientOcclusionRadius()).putFloat(settings.ambientOcclusionIntensity())
                .putFloat(SELF_OCCLUSION_BIAS).putFloat(settings.ambientOcclusionPower());
        uboScratch.putFloat(camera.nearPlane()).putFloat(camera.farPlane())
                .putFloat(sceneNormal == null ? 0.0f : 1.0f).putFloat(0.0f);
        uboScratch.flip();
        backend.writeBuffer(ssaoUbo, uboScratch, 0L);
    }

    void render() {
        backend.beginPass(rawTarget, PassClear.none());
        backend.execute(DrawCommand.of(ssaoPipeline, quad.mesh(), ssaoBindings));
        backend.endPass();
        backend.beginPass(horizontalTarget, PassClear.none());
        backend.execute(DrawCommand.of(horizontalBlurPipeline, quad.mesh(), horizontalBlurBindings));
        backend.endPass();
        backend.beginPass(blurredTarget, PassClear.none());
        backend.execute(DrawCommand.of(verticalBlurPipeline, quad.mesh(), verticalBlurBindings));
        backend.endPass();
    }

    private void createTargets(TextureHandle sceneDepth, int width, int height) {
        fullResolution = settings.ambientOcclusionFullResolution();
        int divisor = fullResolution ? 1 : HALF_RESOLUTION_DIVISOR;
        int targetWidth = Math.max(1, width / divisor);
        int targetHeight = Math.max(1, height / divisor);
        rawTexture = createOcclusionTexture(targetWidth, targetHeight);
        horizontalTexture = createOcclusionTexture(targetWidth, targetHeight);
        blurredTexture = createOcclusionTexture(targetWidth, targetHeight);
        rawTarget = createOcclusionTarget(targetWidth, targetHeight, rawTexture);
        horizontalTarget = createOcclusionTarget(targetWidth, targetHeight, horizontalTexture);
        blurredTarget = createOcclusionTarget(targetWidth, targetHeight, blurredTexture);
        createBindings(sceneDepth);
    }

    private TextureHandle createOcclusionTexture(int width, int height) {
        return backend.createTexture(new TextureDescriptor(width, height, TextureFormat.RGBA8, TextureUsage.SAMPLED));
    }

    private RenderTargetHandle createOcclusionTarget(int width, int height, TextureHandle texture) {
        return backend.createRenderTarget(new RenderTargetDescriptor(width, height, List.of(texture), Optional.empty()));
    }

    private void createBindings(TextureHandle sceneDepth) {
        ssaoBindings = backend.createBindingSet(new BindingSetDescriptor(ssaoLayout(), List.of(
                new Binding(0, new SampledTextureBinding(sceneDepth)),
                new Binding(1, UniformBufferBinding.whole(ssaoUbo, SSAO_UBO_SIZE)),
                new Binding(2, new SampledTextureBinding(sceneNormal == null ? sceneDepth : sceneNormal))
        )));
        horizontalBlurBindings = createBlurBindings(rawTexture, sceneDepth);
        verticalBlurBindings = createBlurBindings(horizontalTexture, sceneDepth);
    }

    private BindingSetHandle createBlurBindings(TextureHandle source, TextureHandle sceneDepth) {
        return backend.createBindingSet(new BindingSetDescriptor(blurLayout(), List.of(
                new Binding(0, new SampledTextureBinding(source)),
                new Binding(1, UniformBufferBinding.whole(ssaoUbo, SSAO_UBO_SIZE)),
                new Binding(2, new SampledTextureBinding(sceneDepth))
        )));
    }

    private BindingSetLayout ssaoLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D)));
    }

    private BindingSetLayout blurLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D)));
    }

    private PipelineHandle createPipeline(String fragmentPath, BindingSetLayout layout) {
        ShaderSource source = new ShaderSource(
                shaderLoader.load("post.vert.glsl").source(),
                shaderLoader.load(fragmentPath).source());
        return backend.createPipeline(new PipelineDescriptor(source, FullscreenQuad.LAYOUT, PASS_STATE, layout));
    }

    private void destroyTargets() {
        backend.destroy(ssaoBindings);
        backend.destroy(horizontalBlurBindings);
        backend.destroy(verticalBlurBindings);
        backend.destroy(rawTarget);
        backend.destroy(horizontalTarget);
        backend.destroy(blurredTarget);
        backend.destroy(rawTexture);
        backend.destroy(horizontalTexture);
        backend.destroy(blurredTexture);
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        destroyTargets();
        backend.destroy(ssaoPipeline);
        backend.destroy(horizontalBlurPipeline);
        backend.destroy(verticalBlurPipeline);
        backend.destroy(ssaoUbo);
        backend = null;
    }
}
