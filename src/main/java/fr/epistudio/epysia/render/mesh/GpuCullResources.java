package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class GpuCullResources {

    private final BufferHandle visibleInstances;
    private final BufferHandle indirectArguments;
    private final BufferHandle visibilityState;
    private final BufferHandle parameters;
    private final BindingSetHandle cullBindings;
    private final int capacity;
    private final TextureHandle pyramid;
    private final BufferHandle source;

    private GpuCullResources(BufferHandle visibleInstances, BufferHandle indirectArguments,
                             BufferHandle visibilityState, BufferHandle parameters,
                             BindingSetHandle cullBindings, int capacity,
                             TextureHandle pyramid, BufferHandle source) {
        this.visibleInstances = visibleInstances;
        this.indirectArguments = indirectArguments;
        this.visibilityState = visibilityState;
        this.parameters = parameters;
        this.cullBindings = cullBindings;
        this.capacity = capacity;
        this.pyramid = pyramid;
        this.source = source;
    }

    static GpuCullResources create(RenderBackend backend, BufferHandle sourceInstances,
                                   TextureHandle pyramid, int capacity) {
        BufferHandle visible = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(capacity * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES)));
        BufferHandle indirect = backend.createBuffer(new BufferDescriptor(BufferUsage.INDIRECT,
                BufferUtils.createByteBuffer(GpuInstanceCuller.INDIRECT_ARGUMENT_BYTES)));
        BufferHandle state = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(capacity * Integer.BYTES)));
        BufferHandle parameters = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(GpuInstanceCuller.PARAMETERS_BYTES)));
        BindingSetHandle bindings = backend.createBindingSet(new BindingSetDescriptor(
                GpuInstanceCuller.layout(), List.of(
                        new Binding(GpuInstanceCuller.SOURCE_INSTANCES_BINDING,
                                StorageBufferBinding.whole(sourceInstances,
                                        (long) capacity * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES)),
                        new Binding(GpuInstanceCuller.VISIBLE_INSTANCES_BINDING,
                                StorageBufferBinding.whole(visible,
                                        (long) capacity * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES)),
                        new Binding(GpuInstanceCuller.INDIRECT_ARGUMENTS_BINDING,
                                StorageBufferBinding.whole(indirect,
                                        GpuInstanceCuller.INDIRECT_ARGUMENT_BYTES)),
                        new Binding(GpuInstanceCuller.VISIBILITY_STATE_BINDING,
                                StorageBufferBinding.whole(state, (long) capacity * Integer.BYTES)),
                        new Binding(GpuInstanceCuller.PYRAMID_BINDING, new SampledTextureBinding(pyramid)),
                        new Binding(GpuInstanceCuller.PARAMETERS_BINDING,
                                UniformBufferBinding.whole(parameters, GpuInstanceCuller.PARAMETERS_BYTES)))));
        return new GpuCullResources(visible, indirect, state, parameters, bindings, capacity,
                pyramid, sourceInstances);
    }

    BufferHandle visibleInstances() {
        return visibleInstances;
    }

    BufferHandle indirectArguments() {
        return indirectArguments;
    }

    BufferHandle parameters() {
        return parameters;
    }

    BindingSetHandle cullBindings() {
        return cullBindings;
    }

    int capacity() {
        return capacity;
    }

    TextureHandle pyramid() {
        return pyramid;
    }

    BufferHandle source() {
        return source;
    }

    void resetIndirectArguments(RenderBackend backend, int indexCount, int firstIndex) {
        ByteBuffer arguments = BufferUtils.createByteBuffer(GpuInstanceCuller.INDIRECT_ARGUMENT_BYTES);
        arguments.putInt(indexCount).putInt(0).putInt(firstIndex).putInt(0).putInt(0);
        arguments.flip();
        backend.writeBuffer(indirectArguments, arguments, 0L);
    }

    void destroy(RenderBackend backend) {
        backend.destroy(cullBindings);
        backend.destroy(visibleInstances);
        backend.destroy(indirectArguments);
        backend.destroy(visibilityState);
        backend.destroy(parameters);
    }
}
