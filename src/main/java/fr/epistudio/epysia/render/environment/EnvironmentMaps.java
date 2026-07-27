package fr.epistudio.epysia.render.environment;

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
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class EnvironmentMaps {

    private static final int ENVIRONMENT_SIZE = 128;
    private static final int IRRADIANCE_SIZE = 32;
    private static final int PREFILTER_SIZE = 128;
    private static final int PREFILTER_MIP_COUNT = 5;
    private static final int BRDF_LUT_SIZE = 256;
    private static final int CAPTURE_UBO_SIZE = 80;
    private static final int FACE_COUNT = 6;

    private static final RenderState CAPTURE_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final ByteBuffer captureScratch = BufferUtils.createByteBuffer(CAPTURE_UBO_SIZE);

    private RenderBackend backend;
    private TextureHandle environmentCubemap;
    private TextureHandle irradianceCubemap;
    private TextureHandle prefilteredCubemap;
    private TextureHandle brdfLut;
    private BufferHandle captureUbo;
    private PipelineHandle skyCapturePipeline;
    private PipelineHandle irradiancePipeline;
    private PipelineHandle prefilterPipeline;
    private PipelineHandle brdfPipeline;
    private BindingSetHandle uboOnlyBindings;
    private BindingSetHandle skyCaptureBindings;
    private BindingSetHandle environmentSamplerBindings;
    private RenderTargetHandle[] environmentTargets;
    private RenderTargetHandle[] irradianceTargets;
    private RenderTargetHandle[][] prefilterTargets;
    private RenderTargetHandle brdfTarget;

    EnvironmentMaps(ShaderLoader shaderLoader, FullscreenQuad quad) {
        this.shaderLoader = shaderLoader;
        this.quad = quad;
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        createTextures();
        captureUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(CAPTURE_UBO_SIZE)));
        createPipelines();
        createBindings();
        createTargets();
        bakeBrdfLut();
    }

    TextureHandle irradiance() {
        return irradianceCubemap;
    }

    TextureHandle prefiltered() {
        return prefilteredCubemap;
    }

    TextureHandle brdfLut() {
        return brdfLut;
    }

    void bake(Vector3f sunDirection, float skyIntensity) {
        for (int face = 0; face < FACE_COUNT; face++) {
            writeCaptureUbo(face, sunDirection, skyIntensity, 0.0f);
            runCapturePass(environmentTargets[face], skyCapturePipeline, skyCaptureBindings);
        }
        for (int face = 0; face < FACE_COUNT; face++) {
            writeCaptureUbo(face, sunDirection, skyIntensity, 0.0f);
            runCapturePass(irradianceTargets[face], irradiancePipeline, environmentSamplerBindings);
        }
        for (int mip = 0; mip < PREFILTER_MIP_COUNT; mip++) {
            float roughness = mip / (float) (PREFILTER_MIP_COUNT - 1);
            for (int face = 0; face < FACE_COUNT; face++) {
                writeCaptureUbo(face, sunDirection, skyIntensity, roughness);
                runCapturePass(prefilterTargets[mip][face], prefilterPipeline, environmentSamplerBindings);
            }
        }
    }

    private void bakeBrdfLut() {
        writeCaptureUbo(0, new Vector3f(0.0f, 1.0f, 0.0f), 1.0f, 0.0f);
        runCapturePass(brdfTarget, brdfPipeline, uboOnlyBindings);
    }

    private void runCapturePass(RenderTargetHandle target, PipelineHandle pipeline, BindingSetHandle bindings) {
        backend.beginPass(target, PassClear.color(0.0f, 0.0f, 0.0f));
        backend.execute(DrawCommand.of(pipeline, quad.mesh(), bindings));
        backend.endPass();
    }

    private void writeCaptureUbo(int face, Vector3f sunDirection, float skyIntensity, float roughness) {
        CubeFaceBasis basis = CubeFaceBasis.FACES[face];
        captureScratch.clear();
        putVector(basis.forward());
        putVector(basis.right());
        putVector(basis.up());
        captureScratch.putFloat(sunDirection.x).putFloat(sunDirection.y).putFloat(sunDirection.z).putFloat(skyIntensity);
        captureScratch.putFloat(roughness).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        captureScratch.flip();
        backend.writeBuffer(captureUbo, captureScratch, 0L);
    }

    private void putVector(Vector3f vector) {
        captureScratch.putFloat(vector.x).putFloat(vector.y).putFloat(vector.z).putFloat(0.0f);
    }

    private void createTextures() {
        environmentCubemap = backend.createTexture(TextureDescriptor.cubemap(ENVIRONMENT_SIZE, TextureFormat.RGBA16F, 1));
        irradianceCubemap = backend.createTexture(TextureDescriptor.cubemap(IRRADIANCE_SIZE, TextureFormat.RGBA16F, 1));
        prefilteredCubemap = backend.createTexture(TextureDescriptor.cubemap(PREFILTER_SIZE, TextureFormat.RGBA16F, PREFILTER_MIP_COUNT));
        brdfLut = backend.createTexture(new TextureDescriptor(BRDF_LUT_SIZE, BRDF_LUT_SIZE,
                TextureFormat.RGBA16F, TextureUsage.SAMPLED));
    }

    private void createPipelines() {
        BindingSetLayout uboOnly = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER)));
        BindingSetLayout withEnvironment = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_CUBE)));
        irradiancePipeline = createCapturePipeline("environment/irradiance_convolve.frag.glsl", withEnvironment);
        prefilterPipeline = createCapturePipeline("environment/specular_prefilter.frag.glsl", withEnvironment);
        brdfPipeline = createCapturePipeline("environment/brdf_lut.frag.glsl", uboOnly);
        rebuildSkyCapture(SkySource.PROCEDURAL);
    }

    void rebuildSkyCapture(SkySource source) {
        destroySkyCapture();
        BindingSetLayout layout = skyCaptureLayout(source);
        ShaderSource shaders = new ShaderSource(
                shaderLoader.load("post.vert.glsl").source(),
                SkyShaderComposer.compose(shaderLoader, "environment/sky_capture.frag.glsl", source).source());
        skyCapturePipeline = backend.createPipeline(
                new PipelineDescriptor(shaders, FullscreenQuad.LAYOUT, CAPTURE_STATE, layout));
        skyCaptureBindings = backend.createBindingSet(
                new BindingSetDescriptor(layout, skyCaptureBindings(source)));
    }

    private static BindingSetLayout skyCaptureLayout(SkySource source) {
        List<BindingSlot> slots = new java.util.ArrayList<>(
                List.of(new BindingSlot(0, BindingType.UNIFORM_BUFFER)));
        if (source.needsTexture()) {
            slots.add(new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D));
        }
        return new BindingSetLayout(slots);
    }

    private List<Binding> skyCaptureBindings(SkySource source) {
        List<Binding> list = new java.util.ArrayList<>(
                List.of(new Binding(0, UniformBufferBinding.whole(captureUbo, CAPTURE_UBO_SIZE))));
        source.texture().ifPresent(texture -> list.add(new Binding(1, new SampledTextureBinding(texture))));
        return list;
    }

    private void destroySkyCapture() {
        if (skyCaptureBindings != null) {
            backend.destroy(skyCaptureBindings);
            skyCaptureBindings = null;
        }
        if (skyCapturePipeline != null) {
            backend.destroy(skyCapturePipeline);
            skyCapturePipeline = null;
        }
    }

    private PipelineHandle createCapturePipeline(String fragmentPath, BindingSetLayout layout) {
        ShaderSource source = new ShaderSource(
                shaderLoader.load("post.vert.glsl").source(),
                shaderLoader.load(fragmentPath).source());
        return backend.createPipeline(new PipelineDescriptor(source, FullscreenQuad.LAYOUT, CAPTURE_STATE, layout));
    }

    private void createBindings() {
        uboOnlyBindings = backend.createBindingSet(new BindingSetDescriptor(
                new BindingSetLayout(List.of(new BindingSlot(0, BindingType.UNIFORM_BUFFER))),
                List.of(new Binding(0, UniformBufferBinding.whole(captureUbo, CAPTURE_UBO_SIZE)))));
        environmentSamplerBindings = backend.createBindingSet(new BindingSetDescriptor(
                new BindingSetLayout(List.of(
                        new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                        new BindingSlot(1, BindingType.SAMPLED_TEXTURE_CUBE))),
                List.of(
                        new Binding(0, UniformBufferBinding.whole(captureUbo, CAPTURE_UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(environmentCubemap)))));
    }

    private void createTargets() {
        environmentTargets = createCubeTargets(environmentCubemap, ENVIRONMENT_SIZE, 0);
        irradianceTargets = createCubeTargets(irradianceCubemap, IRRADIANCE_SIZE, 0);
        prefilterTargets = new RenderTargetHandle[PREFILTER_MIP_COUNT][];
        for (int mip = 0; mip < PREFILTER_MIP_COUNT; mip++) {
            prefilterTargets[mip] = createCubeTargets(prefilteredCubemap, PREFILTER_SIZE >> mip, mip);
        }
        brdfTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                BRDF_LUT_SIZE, BRDF_LUT_SIZE, List.of(brdfLut), java.util.Optional.empty()));
    }

    private RenderTargetHandle[] createCubeTargets(TextureHandle cubemap, int size, int mipLevel) {
        RenderTargetHandle[] targets = new RenderTargetHandle[FACE_COUNT];
        for (int face = 0; face < FACE_COUNT; face++) {
            targets[face] = backend.createRenderTarget(RenderTargetDescriptor.cubeFace(size, cubemap, face, mipLevel));
        }
        return targets;
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        destroyTargets();
        destroySkyCapture();
        backend.destroy(uboOnlyBindings);
        backend.destroy(environmentSamplerBindings);
        backend.destroy(irradiancePipeline);
        backend.destroy(prefilterPipeline);
        backend.destroy(brdfPipeline);
        backend.destroy(captureUbo);
        backend.destroy(environmentCubemap);
        backend.destroy(irradianceCubemap);
        backend.destroy(prefilteredCubemap);
        backend.destroy(brdfLut);
        backend = null;
    }

    private void destroyTargets() {
        for (RenderTargetHandle target : environmentTargets) {
            backend.destroy(target);
        }
        for (RenderTargetHandle target : irradianceTargets) {
            backend.destroy(target);
        }
        for (RenderTargetHandle[] mipTargets : prefilterTargets) {
            for (RenderTargetHandle target : mipTargets) {
                backend.destroy(target);
            }
        }
        backend.destroy(brdfTarget);
    }
}
