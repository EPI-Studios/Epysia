package com.meekdev.psyhou.game;

import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.FullscreenQuad;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;

import java.util.List;

public final class EdgeOutlinePassSystem implements RenderSystem {

    public static final RenderPass EDGE_OUTLINE =
            RenderPasses.register("PROJECT_EDGE_OUTLINE", RenderPasses.POST_ORDER + 10);

    private static final int SCENE_DEPTH_BINDING = 0;
    private static final String VERTEX_PATH = "example/edge_outline.vert.glsl";
    private static final String FRAGMENT_PATH = "example/edge_outline.frag.glsl";

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshSystem;
    private final PostProcessSystem postProcess;
    private final FullscreenQuad quad = new FullscreenQuad();

    private RenderBackend backend;
    private BindingSetLayout layout;
    private PipelineHandle pipeline;
    private BindingSetHandle bindings;

    public EdgeOutlinePassSystem(ShaderLoader shaderLoader, MeshRenderSystem meshSystem,
                                 PostProcessSystem postProcess) {
        this.shaderLoader = shaderLoader;
        this.meshSystem = meshSystem;
        this.postProcess = postProcess;
    }

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        quad.initialize(backend);
        layout = new BindingSetLayout(List.of(
                new BindingSlot(SCENE_DEPTH_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER)));
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        pipeline = backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(vertex.source(), fragment.source()),
                FullscreenQuad.LAYOUT, RenderState.TRANSPARENT_3D, layout));
        createBindings();
        configurer.bindStageTargetFollowing(EDGE_OUTLINE, RenderPasses.POST, PassClear.none());
    }

    @Override
    public void onResize(RenderBackend renderBackend, StageConfigurer configurer, int width, int height) {
        backend.destroy(bindings);
        createBindings();
    }

    private void createBindings() {
        bindings = backend.createBindingSet(new BindingSetDescriptor(layout, List.of(
                new Binding(SCENE_DEPTH_BINDING, new SampledTextureBinding(postProcess.sceneDepthTexture())),
                new Binding(MeshShaderBindings.FRAME_UBO_BINDING, UniformBufferBinding.whole(
                        meshSystem.frameUniformBuffer(), MeshShaderBindings.FRAME_UBO_SIZE)))));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        frame.submit(EDGE_OUTLINE, DrawCommand.of(pipeline, quad.mesh(), bindings));
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        renderBackend.destroy(bindings);
        renderBackend.destroy(pipeline);
        quad.shutdown();
    }
}
