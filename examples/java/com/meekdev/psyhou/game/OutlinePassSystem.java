package com.meekdev.psyhou.game;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
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
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.mesh.UploadedSubmesh;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class OutlinePassSystem implements RenderSystem {

    public static final RenderPass OUTLINE =
            RenderPasses.register("PROJECT_OUTLINE", RenderPasses.OPAQUE_3D_ORDER + 10);

    private static final String VERTEX_PATH = "example/outline.vert.glsl";
    private static final String FRAGMENT_PATH = "example/outline.frag.glsl";
    private static final long SINGLE_TRANSFORM_BYTES = MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;

    private static final VertexLayout MESH_LAYOUT = new VertexLayout(List.of(
            new VertexAttribute(0, VertexFormat.FLOAT3, 0),
            new VertexAttribute(1, VertexFormat.FLOAT3, 12),
            new VertexAttribute(2, VertexFormat.FLOAT2, 24),
            new VertexAttribute(3, VertexFormat.FLOAT3, 32)
    ), MeshShaderBindings.VERTEX_STRIDE);

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshSystem;
    private final Map<MeshRenderer, TransformResources> singleObjects = new IdentityHashMap<>();
    private final Map<MultiMeshRenderer, BindingSetHandle> instancedBindings = new IdentityHashMap<>();
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final List<BindingSetHandle> ownedBindings = new ArrayList<>();
    private final ByteBuffer scratchTransform =
            BufferUtils.createByteBuffer((int) SINGLE_TRANSFORM_BYTES);
    private final Matrix4f scratchNormalMatrix = new Matrix4f();

    private RenderBackend backend;
    private BindingSetLayout layout;
    private PipelineHandle pipeline;

    public OutlinePassSystem(ShaderLoader shaderLoader, MeshRenderSystem meshSystem) {
        this.shaderLoader = shaderLoader;
        this.meshSystem = meshSystem;
    }

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        layout = new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER)));
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.LESS_EQUAL,
                BlendMode.OPAQUE, CullMode.FRONT);
        pipeline = backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(vertex.source(), fragment.source()), MESH_LAYOUT, state, layout));
        configurer.bindStageTargetFollowing(OUTLINE, RenderPasses.OPAQUE_3D, PassClear.none());
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        float alpha = context.interpolationAlpha();
        for (GameObject gameObject : scene.gameObjects()) {
            submitSingleObject(gameObject, frame, alpha);
            submitInstanced(gameObject, frame);
        }
    }

    private void submitSingleObject(GameObject gameObject, FrameBuilder frame, float alpha) {
        MeshRenderer renderer = gameObject.getComponentOrNull(MeshRenderer.class);
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (renderer == null || transform == null) {
            return;
        }
        UploadedMesh mesh = renderer.mesh().orElse(null);
        if (mesh == null) {
            return;
        }
        TransformResources resources = singleObjects.computeIfAbsent(renderer, ignored -> createSingleObject());
        writeTransform(resources.buffer(), transform.worldMatrix(alpha));
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            frame.submit(OUTLINE, new DrawCommand(pipeline, submesh.handle(), resources.bindings(), 0L, 1));
        }
    }

    private void submitInstanced(GameObject gameObject, FrameBuilder frame) {
        MultiMeshRenderer renderer = gameObject.getComponentOrNull(MultiMeshRenderer.class);
        if (renderer == null || renderer.instanceCount() == 0) {
            return;
        }
        UploadedMesh mesh = renderer.mesh().orElse(null);
        BufferHandle instances = meshSystem.instancedPass().instanceBufferFor(renderer).orElse(null);
        if (mesh == null || instances == null) {
            return;
        }
        BindingSetHandle bindings = instancedBindings.computeIfAbsent(renderer,
                ignored -> createBindings(instances, renderer.instanceCount() * SINGLE_TRANSFORM_BYTES));
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            frame.submit(OUTLINE, new DrawCommand(pipeline, submesh.handle(), bindings, 0L,
                    renderer.instanceCount()));
        }
    }

    private TransformResources createSingleObject() {
        BufferHandle buffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer((int) SINGLE_TRANSFORM_BYTES)));
        ownedBuffers.add(buffer);
        return new TransformResources(buffer, createBindings(buffer, SINGLE_TRANSFORM_BYTES));
    }

    private BindingSetHandle createBindings(BufferHandle transforms, long byteSize) {
        BindingSetHandle bindings = backend.createBindingSet(new BindingSetDescriptor(layout, List.of(
                new Binding(MeshShaderBindings.FRAME_UBO_BINDING, UniformBufferBinding.whole(
                        meshSystem.frameUniformBuffer(), MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        StorageBufferBinding.whole(transforms, byteSize)))));
        ownedBindings.add(bindings);
        return bindings;
    }

    private void writeTransform(BufferHandle buffer, Matrix4f model) {
        scratchTransform.clear();
        model.get(0, scratchTransform);
        model.normal(scratchNormalMatrix);
        scratchNormalMatrix.get(64, scratchTransform);
        scratchTransform.position(0);
        scratchTransform.limit((int) SINGLE_TRANSFORM_BYTES);
        backend.writeBuffer(buffer, scratchTransform, 0L);
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        for (BindingSetHandle bindings : ownedBindings) {
            renderBackend.destroy(bindings);
        }
        for (BufferHandle buffer : ownedBuffers) {
            renderBackend.destroy(buffer);
        }
        renderBackend.destroy(pipeline);
    }

    private record TransformResources(BufferHandle buffer, BindingSetHandle bindings) {
    }
}
