package fr.epistudio.epysia.render.postfx;

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
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

final class BloomChain {

    private static final int MIP_COUNT = 5;
    private static final int BLOOM_UBO_SIZE = 16;
    private static final RenderState FILTER_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);
    private static final RenderState ADDITIVE_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.ADDITIVE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(BLOOM_UBO_SIZE);

    private RenderBackend backend;
    private PipelineHandle prefilterPipeline;
    private PipelineHandle downsamplePipeline;
    private PipelineHandle upsamplePipeline;
    private BufferHandle bloomUbo;
    private TextureHandle[] mipTextures;
    private RenderTargetHandle[] mipTargets;
    private BindingSetHandle prefilterBindings;
    private BindingSetHandle[] downsampleBindings;
    private BindingSetHandle[] upsampleBindings;

    BloomChain(ShaderLoader shaderLoader, FullscreenQuad quad) {
        this.shaderLoader = shaderLoader;
        this.quad = quad;
    }

    void initialize(RenderBackend backend, TextureHandle sceneColor, int width, int height) {
        this.backend = backend;
        bloomUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(BLOOM_UBO_SIZE), true));
        prefilterPipeline = createPipeline("postfx/bloom_prefilter.frag.glsl", prefilterLayout(), FILTER_STATE);
        downsamplePipeline = createPipeline("postfx/bloom_downsample.frag.glsl", samplerOnlyLayout(), FILTER_STATE);
        upsamplePipeline = createPipeline("postfx/bloom_upsample.frag.glsl", samplerOnlyLayout(), ADDITIVE_STATE);
        createChain(sceneColor, width, height);
    }

    void onResize(TextureHandle sceneColor, int width, int height) {
        destroyChain();
        createChain(sceneColor, width, height);
    }

    TextureHandle bloomTexture() {
        return mipTextures[0];
    }

    void writeUbo(PostProcessSettings settings) {
        uboScratch.clear();
        uboScratch.putFloat(settings.bloomThreshold()).putFloat(Math.max(settings.bloomKnee(), 1.0e-3f))
                .putFloat(0.0f).putFloat(0.0f);
        uboScratch.flip();
        backend.writeBuffer(bloomUbo, uboScratch, 0L);
    }

    void render() {
        backend.beginPass(mipTargets[0], PassClear.none());
        backend.execute(DrawCommand.of(prefilterPipeline, quad.mesh(), prefilterBindings));
        backend.endPass();
        for (int mip = 1; mip < MIP_COUNT; mip++) {
            backend.beginPass(mipTargets[mip], PassClear.none());
            backend.execute(DrawCommand.of(downsamplePipeline, quad.mesh(), downsampleBindings[mip - 1]));
            backend.endPass();
        }
        for (int mip = MIP_COUNT - 2; mip >= 0; mip--) {
            backend.beginPass(mipTargets[mip], PassClear.none());
            backend.execute(DrawCommand.of(upsamplePipeline, quad.mesh(), upsampleBindings[mip]));
            backend.endPass();
        }
    }

    private void createChain(TextureHandle sceneColor, int width, int height) {
        mipTextures = new TextureHandle[MIP_COUNT];
        mipTargets = new RenderTargetHandle[MIP_COUNT];
        for (int mip = 0; mip < MIP_COUNT; mip++) {
            int mipWidth = Math.max(1, width >> (mip + 1));
            int mipHeight = Math.max(1, height >> (mip + 1));
            mipTextures[mip] = backend.createTexture(new TextureDescriptor(
                    mipWidth, mipHeight, TextureFormat.RGBA16F, TextureUsage.SAMPLED));
            mipTargets[mip] = backend.createRenderTarget(new RenderTargetDescriptor(
                    mipWidth, mipHeight, List.of(mipTextures[mip]), Optional.empty()));
        }
        createBindings(sceneColor);
    }

    private void createBindings(TextureHandle sceneColor) {
        prefilterBindings = backend.createBindingSet(new BindingSetDescriptor(prefilterLayout(), List.of(
                new Binding(0, new SampledTextureBinding(sceneColor)),
                new Binding(1, UniformBufferBinding.whole(bloomUbo, BLOOM_UBO_SIZE))
        )));
        downsampleBindings = new BindingSetHandle[MIP_COUNT - 1];
        upsampleBindings = new BindingSetHandle[MIP_COUNT - 1];
        for (int i = 0; i < MIP_COUNT - 1; i++) {
            downsampleBindings[i] = samplerBindingSet(mipTextures[i]);
            upsampleBindings[i] = samplerBindingSet(mipTextures[i + 1]);
        }
    }

    private BindingSetHandle samplerBindingSet(TextureHandle source) {
        return backend.createBindingSet(new BindingSetDescriptor(samplerOnlyLayout(),
                List.of(new Binding(0, new SampledTextureBinding(source)))));
    }

    private BindingSetLayout prefilterLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER)));
    }

    private BindingSetLayout samplerOnlyLayout() {
        return new BindingSetLayout(List.of(new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D)));
    }

    private PipelineHandle createPipeline(String fragmentPath, BindingSetLayout layout, RenderState state) {
        ShaderSource source = new ShaderSource(
                shaderLoader.load("post.vert.glsl").source(),
                shaderLoader.load(fragmentPath).source());
        return backend.createPipeline(new PipelineDescriptor(source, FullscreenQuad.LAYOUT, state, layout));
    }

    private void destroyChain() {
        backend.destroy(prefilterBindings);
        for (int i = 0; i < MIP_COUNT - 1; i++) {
            backend.destroy(downsampleBindings[i]);
            backend.destroy(upsampleBindings[i]);
        }
        for (int mip = 0; mip < MIP_COUNT; mip++) {
            backend.destroy(mipTargets[mip]);
            backend.destroy(mipTextures[mip]);
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        destroyChain();
        backend.destroy(prefilterPipeline);
        backend.destroy(downsamplePipeline);
        backend.destroy(upsamplePipeline);
        backend.destroy(bloomUbo);
        backend = null;
    }
}
