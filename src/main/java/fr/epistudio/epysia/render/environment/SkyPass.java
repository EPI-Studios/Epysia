package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
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
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class SkyPass {

    private static final int SKY_UBO_SIZE = 112;
    private static final RenderState SKY_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.LESS_EQUAL, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(SKY_UBO_SIZE);
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private final long startNanos = System.nanoTime();
    private final Vector3f scratchCameraPosition = new Vector3f();

    private RenderBackend backend;
    private PipelineHandle pipeline;
    private BufferHandle skyUbo;
    private BindingSetHandle bindings;

    SkyPass(ShaderLoader shaderLoader, FullscreenQuad quad) {
        this.shaderLoader = shaderLoader;
        this.quad = quad;
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        skyUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(SKY_UBO_SIZE)));
        rebuild(SkySource.PROCEDURAL);
    }

    void rebuild(SkySource source) {
        destroyPipeline();
        BindingSetLayout layout = layoutFor(source);
        ShaderSource shaders = new ShaderSource(
                shaderLoader.load("sky.vert.glsl").source(),
                SkyShaderComposer.compose(shaderLoader, "sky.frag.glsl", source).source());
        pipeline = backend.createPipeline(new PipelineDescriptor(shaders, FullscreenQuad.LAYOUT, SKY_STATE, layout));
        bindings = backend.createBindingSet(new BindingSetDescriptor(layout, bindingsFor(source)));
    }

    private static BindingSetLayout layoutFor(SkySource source) {
        List<BindingSlot> slots = new ArrayList<>(List.of(new BindingSlot(0, BindingType.UNIFORM_BUFFER)));
        if (source.needsTexture()) {
            slots.add(new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D));
        }
        return new BindingSetLayout(slots);
    }

    private List<Binding> bindingsFor(SkySource source) {
        List<Binding> list = new ArrayList<>(
                List.of(new Binding(0, UniformBufferBinding.whole(skyUbo, SKY_UBO_SIZE))));
        source.texture().ifPresent(texture -> list.add(new Binding(1, new SampledTextureBinding(texture))));
        return list;
    }

    private void destroyPipeline() {
        if (bindings != null) {
            backend.destroy(bindings);
            bindings = null;
        }
        if (pipeline != null) {
            backend.destroy(pipeline);
            pipeline = null;
        }
    }

    void collect(Camera3D camera, Vector3f sunDirection, float skyIntensity, FrameBuilder frame, float alpha) {
        writeUbo(camera, sunDirection, skyIntensity, alpha);
        frame.submit(RenderPasses.OPAQUE_3D, DrawCommand.of(pipeline, quad.mesh(), bindings, Long.MAX_VALUE));
    }

    private void writeUbo(Camera3D camera, Vector3f sunDirection, float skyIntensity, float alpha) {
        camera.cullingViewProjection(alpha).invert(scratchInverseViewProjection);
        camera.position(scratchCameraPosition, alpha);
        uboScratch.clear();
        scratchInverseViewProjection.get(0, uboScratch);
        uboScratch.position(64);
        uboScratch.putFloat(scratchCameraPosition.x).putFloat(scratchCameraPosition.y)
                .putFloat(scratchCameraPosition.z).putFloat(0.0f);
        uboScratch.putFloat(sunDirection.x).putFloat(sunDirection.y)
                .putFloat(sunDirection.z).putFloat(skyIntensity);
        float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
        uboScratch.putFloat(elapsed).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        uboScratch.flip();
        backend.writeBuffer(skyUbo, uboScratch, 0L);
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        destroyPipeline();
        backend.destroy(skyUbo);
        backend = null;
    }
}
