package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MultiDrawGroup {

    private static final int MINIMUM_CAPACITY = 64;

    private final RenderBackend backend;
    private final MeshArena arena;
    private final List<ArenaMesh> allocations = new ArrayList<>();
    private final Matrix4f scratchNormal = new Matrix4f();

    private ByteBuffer transformStaging;
    private BufferHandle transforms;
    private BufferHandle drawIndices;
    private MultiDrawBuffer commands;
    private BindingSetHandle bindings;
    private PipelineHandle pipeline;
    private int capacity;
    private long minimumDepthBits = Long.MAX_VALUE;

    MultiDrawGroup(RenderBackend backend, MeshArena arena) {
        this.backend = backend;
        this.arena = arena;
    }

    public MeshArena arena() {
        return arena;
    }

    public BufferHandle transforms() {
        return transforms;
    }

    public int pendingCount() {
        return allocations.size();
    }

    public void begin() {
        allocations.clear();
        minimumDepthBits = Long.MAX_VALUE;
        if (transformStaging != null) {
            transformStaging.clear();
        }
    }

    public void ensureCapacity(int wanted) {
        if (capacity >= wanted && transforms != null) {
            return;
        }
        int grown = Math.max(MINIMUM_CAPACITY, Math.max(wanted, capacity * 2));
        ByteBuffer previous = transformStaging;
        int writtenBytes = allocations.size() * MeshShaderBindings.OBJECT_UBO_SIZE;
        destroyBuffers();
        capacity = grown;
        transformStaging = BufferUtils.createByteBuffer(grown * MeshShaderBindings.OBJECT_UBO_SIZE);
        if (previous != null && writtenBytes > 0) {
            previous.position(0);
            previous.limit(writtenBytes);
            transformStaging.put(previous);
            previous.limit(previous.capacity());
        }
        transformStaging.position(0);
        transformStaging.limit(transformStaging.capacity());
        transforms = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, transformStaging));
        drawIndices = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, drawIndexBytes(grown)));
        commands = MultiDrawBuffer.create(backend, grown);
        bindings = null;
    }

    private static ByteBuffer drawIndexBytes(int count) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(count * Integer.BYTES);
        for (int index = 0; index < count; index++) {
            bytes.putInt(index);
        }
        return bytes.flip();
    }

    public void append(ArenaMesh allocation, Matrix4f model, long depthBits, int layer) {
        int slot = allocations.size();
        allocations.add(allocation);
        minimumDepthBits = Math.min(minimumDepthBits, depthBits);
        int base = slot * MeshShaderBindings.OBJECT_UBO_SIZE;
        model.get(base, transformStaging);
        model.normal(scratchNormal);
        scratchNormal.get(base + 64, transformStaging);
        transformStaging.putFloat(base + MeshShaderBindings.INSTANCE_LAYER_BYTE_OFFSET, layer);
    }

    public boolean hasBindings() {
        return bindings != null;
    }

    public void adoptBindings(PipelineHandle groupPipeline, BindingSetHandle groupBindings) {
        this.pipeline = groupPipeline;
        this.bindings = groupBindings;
    }

    public Optional<DrawCommand> flush() {
        if (allocations.isEmpty() || bindings == null) {
            return Optional.empty();
        }
        transformStaging.position(0);
        transformStaging.limit(allocations.size() * MeshShaderBindings.OBJECT_UBO_SIZE);
        backend.writeBuffer(transforms, transformStaging, 0L);
        transformStaging.limit(transformStaging.capacity());
        commands.begin();
        for (int index = 0; index < allocations.size(); index++) {
            commands.append(allocations.get(index), index);
        }
        commands.upload();
        long sortKey = (pipeline.id() << 32) | minimumDepthBits;
        return Optional.of(DrawCommand.multiDrawIndirect(
                pipeline, arena.boundMesh(), bindings, sortKey,
                commands.buffer(), commands.drawCount(), drawIndices));
    }

    public long transformByteSize() {
        return (long) capacity * MeshShaderBindings.OBJECT_UBO_SIZE;
    }

    private void destroyBuffers() {
        if (transforms != null) {
            backend.destroy(transforms);
            backend.destroy(drawIndices);
            commands.destroy();
        }
    }

    public void destroy() {
        destroyBuffers();
        transforms = null;
        capacity = 0;
    }
}
