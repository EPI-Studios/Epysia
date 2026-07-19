package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.Stage;
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
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class SkyPass {

    private static final int SKY_UBO_SIZE = 96;
    private static final RenderState SKY_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.LESS_EQUAL, BlendMode.OPAQUE, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(SKY_UBO_SIZE);
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
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
        BindingSetLayout layout = new BindingSetLayout(List.of(new BindingSlot(0, BindingType.UNIFORM_BUFFER)));
        ShaderSource source = new ShaderSource(
                shaderLoader.load("sky.vert.glsl").source(),
                shaderLoader.load("sky.frag.glsl").source());
        pipeline = backend.createPipeline(new PipelineDescriptor(source, FullscreenQuad.LAYOUT, SKY_STATE, layout));
        skyUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(SKY_UBO_SIZE)));
        bindings = backend.createBindingSet(new BindingSetDescriptor(layout,
                List.of(new Binding(0, UniformBufferBinding.whole(skyUbo, SKY_UBO_SIZE)))));
    }

    void collect(Camera3D camera, Vector3f sunDirection, float skyIntensity, FrameBuilder frame, float alpha) {
        writeUbo(camera, sunDirection, skyIntensity, alpha);
        frame.submit(Stage.OPAQUE_3D, DrawCommand.of(pipeline, quad.mesh(), bindings, Long.MAX_VALUE));
    }

    private void writeUbo(Camera3D camera, Vector3f sunDirection, float skyIntensity, float alpha) {
        camera.viewProjection(alpha).invert(scratchInverseViewProjection);
        camera.position(scratchCameraPosition, alpha);
        uboScratch.clear();
        scratchInverseViewProjection.get(0, uboScratch);
        uboScratch.position(64);
        uboScratch.putFloat(scratchCameraPosition.x).putFloat(scratchCameraPosition.y)
                .putFloat(scratchCameraPosition.z).putFloat(0.0f);
        uboScratch.putFloat(sunDirection.x).putFloat(sunDirection.y)
                .putFloat(sunDirection.z).putFloat(skyIntensity);
        uboScratch.flip();
        backend.writeBuffer(skyUbo, uboScratch, 0L);
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        backend.destroy(bindings);
        backend.destroy(skyUbo);
        backend.destroy(pipeline);
        backend = null;
    }
}
