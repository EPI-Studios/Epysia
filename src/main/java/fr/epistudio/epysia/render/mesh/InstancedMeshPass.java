package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InstancedMeshPass {

    private static final int INSTANCE_STRIDE_BYTES = 64;

    private static final VertexLayout MESH_LAYOUT = new VertexLayout(List.of(
            new VertexAttribute(0, VertexFormat.FLOAT3, 0),
            new VertexAttribute(1, VertexFormat.FLOAT3, 12),
            new VertexAttribute(2, VertexFormat.FLOAT2, 24),
            new VertexAttribute(3, VertexFormat.FLOAT3, 32)
    ), MeshShaderBindings.VERTEX_STRIDE);

    public static final VertexLayout INSTANCE_LAYOUT = new VertexLayout(List.of(
            new VertexAttribute(4, VertexFormat.FLOAT4, 0),
            new VertexAttribute(5, VertexFormat.FLOAT4, 16),
            new VertexAttribute(6, VertexFormat.FLOAT4, 32),
            new VertexAttribute(7, VertexFormat.FLOAT4, 48)
    ), INSTANCE_STRIDE_BYTES);

    private static final BindingSetLayout FRAME_LIGHT_LAYOUT = new BindingSetLayout(List.of(
            new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
            new BindingSlot(MeshShaderBindings.LIGHT_SSBO_BINDING, BindingType.STORAGE_BUFFER),
            new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER)
    ));

    private final ShaderLoader shaderLoader;
    private final Map<String, PipelineHandle> pipelineCache = new HashMap<>();
    private final Map<MultiMeshRenderer, InstancedResources> resources = new IdentityHashMap<>();
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final List<BindingSetHandle> ownedBindings = new ArrayList<>();

    private RenderBackend backend;
    private BufferHandle frameUbo;
    private LightStorage lightStorage;

    InstancedMeshPass(ShaderLoader shaderLoader) {
        this.shaderLoader = shaderLoader;
    }

    public void initialize(RenderBackend backend, BufferHandle frameUbo, LightStorage lightStorage) {
        this.backend = backend;
        this.frameUbo = frameUbo;
        this.lightStorage = lightStorage;
    }

    void collect(Scene scene, FrameBuilder frame) {
        for (GameObject gameObject : scene.gameObjects()) {
            MultiMeshRenderer renderer = gameObject.getComponent(MultiMeshRenderer.class).orElse(null);
            if (renderer == null) {
                continue;
            }
            UploadedMesh mesh = renderer.mesh().orElse(null);
            Material material = renderer.material().orElse(null);
            if (mesh == null || material == null || renderer.instanceCount() == 0) {
                continue;
            }
            PipelineHandle pipeline = pipelineFor(material);
            InstancedResources instanceResources = resourcesFor(renderer);
            for (UploadedSubmesh submesh : mesh.submeshes()) {
                frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, submesh.handle(),
                        instanceResources.bindingSet(), 0L, renderer.instanceCount()));
            }
        }
    }

    private PipelineHandle pipelineFor(Material material) {
        String key = material.vertexShaderPath() + "|" + material.fragmentShaderPath();
        return pipelineCache.computeIfAbsent(key, ignored -> buildPipeline(material));
    }

    private PipelineHandle buildPipeline(Material material) {
        LoadedShader vertex = shaderLoader.load(material.vertexShaderPath());
        LoadedShader fragment = shaderLoader.load(material.fragmentShaderPath());
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(vertex.source(), fragment.source()),
                MESH_LAYOUT,
                RenderState.OPAQUE_3D,
                FRAME_LIGHT_LAYOUT
        ));
    }

    public Optional<BufferHandle> instanceBufferFor(MultiMeshRenderer renderer) {
        InstancedResources cached = resources.get(renderer);
        return cached == null ? Optional.empty() : Optional.of(cached.instanceBuffer());
    }

    private InstancedResources resourcesFor(MultiMeshRenderer renderer) {
        InstancedResources cached = resources.get(renderer);
        if (cached != null && !renderer.consumeDirty()) {
            return cached;
        }
        if (cached != null) {
            releaseResources(cached);
        }
        InstancedResources rebuilt = createResources(renderer);
        resources.put(renderer, rebuilt);
        return rebuilt;
    }

    private InstancedResources createResources(MultiMeshRenderer renderer) {
        float[] data = renderer.instanceData();
        ByteBuffer bytes = BufferUtils.createByteBuffer(data.length * Float.BYTES);
        bytes.asFloatBuffer().put(data);
        BufferHandle instanceBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, bytes));
        long instanceByteSize = (long) data.length * Float.BYTES;
        BindingSetHandle bindingSet = backend.createBindingSet(new BindingSetDescriptor(
                FRAME_LIGHT_LAYOUT,
                List.of(
                        new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                                UniformBufferBinding.whole(frameUbo, MeshShaderBindings.FRAME_UBO_SIZE)),
                        new Binding(MeshShaderBindings.LIGHT_SSBO_BINDING,
                                StorageBufferBinding.whole(lightStorage.handle(), lightStorage.byteSize())),
                        new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                                StorageBufferBinding.whole(instanceBuffer, instanceByteSize))
                )
        ));
        ownedBuffers.add(instanceBuffer);
        ownedBindings.add(bindingSet);
        return new InstancedResources(instanceBuffer, bindingSet);
    }

    private void releaseResources(InstancedResources instanceResources) {
        backend.destroy(instanceResources.bindingSet());
        backend.destroy(instanceResources.instanceBuffer());
        ownedBindings.remove(instanceResources.bindingSet());
        ownedBuffers.remove(instanceResources.instanceBuffer());
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (BindingSetHandle binding : ownedBindings) {
            backend.destroy(binding);
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        for (PipelineHandle pipeline : pipelineCache.values()) {
            backend.destroy(pipeline);
        }
        ownedBindings.clear();
        ownedBuffers.clear();
        pipelineCache.clear();
        resources.clear();
    }

    private record InstancedResources(BufferHandle instanceBuffer, BindingSetHandle bindingSet) {
    }
}
