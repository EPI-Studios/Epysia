package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class SkinnedDeformer {
    private static final int SOURCE_VERTICES_BINDING = 0;
    private static final int JOINT_PALETTE_BINDING = 1;
    private static final int DEFORMED_VERTICES_BINDING = 2;
    private static final int PARAMETERS_BINDING = 3;
    private static final int PARAMETERS_BYTES = 16;
    private static final int WORKGROUP_SIZE = 64;

    private final RenderBackend backend;
    private final PipelineHandle pipeline;
    private final ByteBuffer scratchParameters = BufferUtils.createByteBuffer(PARAMETERS_BYTES);

    private int deformedThisFrame;

    SkinnedDeformer(RenderBackend backend, ShaderLoader shaderLoader) {
        this.backend = backend;
        this.pipeline = backend.createComputePipeline(new ComputePipelineDescriptor(
                shaderLoader.load("lib/skin_deform.comp.glsl").source(), layout()));
    }

    private static BindingSetLayout layout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(SOURCE_VERTICES_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(JOINT_PALETTE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DEFORMED_VERTICES_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(PARAMETERS_BINDING, BindingType.UNIFORM_BUFFER)));
    }

    static boolean supports(UploadedMesh mesh) {
        return mesh.skinned() && !mesh.arenaBacked() && mesh.vertexCount() > 0;
    }

    DeformedMesh create(UploadedMesh mesh, BufferHandle paletteBuffer, long paletteByteSize) {
        int sourceStride = MeshShaderBindings.vertexStride(true, mesh.vertexColored());
        int deformedStride = MeshShaderBindings.vertexStride(false, mesh.vertexColored());
        BufferHandle deformedVertices = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX,
                BufferUtils.createByteBuffer(mesh.vertexCount() * deformedStride)));
        BufferHandle parameters = createParameterBuffer(mesh, sourceStride, deformedStride);
        BindingSetHandle bindings = backend.createBindingSet(new BindingSetDescriptor(layout(), List.of(
                new Binding(SOURCE_VERTICES_BINDING, StorageBufferBinding.whole(mesh.vertexBuffer(),
                        (long) mesh.vertexCount() * sourceStride)),
                new Binding(JOINT_PALETTE_BINDING, StorageBufferBinding.whole(paletteBuffer, paletteByteSize)),
                new Binding(DEFORMED_VERTICES_BINDING, StorageBufferBinding.whole(deformedVertices,
                        (long) mesh.vertexCount() * deformedStride)),
                new Binding(PARAMETERS_BINDING, UniformBufferBinding.whole(parameters, PARAMETERS_BYTES)))));
        return new DeformedMesh(deformedVertices, parameters, bindings,
                deformedSubmeshes(mesh, deformedVertices), mesh.vertexCount());
    }

    private BufferHandle createParameterBuffer(UploadedMesh mesh, int sourceStride, int deformedStride) {
        scratchParameters.clear();
        scratchParameters.putInt(mesh.vertexCount());
        scratchParameters.putInt(sourceStride / Float.BYTES);
        scratchParameters.putInt(deformedStride / Float.BYTES);
        scratchParameters.putInt(mesh.vertexColored() ? MeshData.COLOR_COMPONENTS : 0);
        scratchParameters.flip();
        return backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, scratchParameters));
    }

    private List<UploadedSubmesh> deformedSubmeshes(UploadedMesh mesh, BufferHandle deformedVertices) {
        List<UploadedSubmesh> deformed = new ArrayList<>(mesh.submeshes().size());
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            MeshHandle source = submesh.handle();
            MeshHandle handle = backend.createMesh(new MeshDescriptor(deformedVertices, mesh.indexBuffer(),
                    backend.meshFirstIndex(source), backend.meshIndexCount(source),
                    backend.meshIndexFormat(source)));
            deformed.add(new UploadedSubmesh(handle, submesh.materialSlot()));
        }
        return deformed;
    }

    void beginFrame() {
        deformedThisFrame = 0;
    }

    void dispatch(DeformedMesh deformed) {
        backend.dispatchCompute(ComputeDispatch.of(pipeline, deformed.bindings(),
                groupCountFor(deformed.vertexCount())));
        deformedThisFrame++;
    }

    void flush() {
        if (deformedThisFrame == 0) {
            return;
        }
        backend.computeBarrier(ComputeBarrier.VERTEX_ATTRIBUTES);
    }

    int deformedThisFrame() {
        return deformedThisFrame;
    }

    private static int groupCountFor(int vertexCount) {
        return (vertexCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
    }
}
