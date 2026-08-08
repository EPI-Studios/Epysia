package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class ObjectUniformArena {
    private static final int SLOTS_PER_BLOCK = 512;

    private final RenderBackend backend;
    private final int slotBytes;
    private final boolean ringBuffered;
    private final List<BufferHandle> blocks = new ArrayList<>();
    private final List<ObjectUniformSlot> freeSlots = new ArrayList<>();
    private int allocatedSlots;

    ObjectUniformArena(RenderBackend backend, boolean ringBuffered) {
        this.backend = backend;
        this.ringBuffered = ringBuffered;
        this.slotBytes = Math.max(MeshShaderBindings.OBJECT_UBO_SIZE, backend.uniformBufferOffsetAlignment());
    }

    ObjectUniformSlot allocate() {
        if (!freeSlots.isEmpty()) {
            ObjectUniformSlot reused = freeSlots.removeLast();
            reused.reset();
            return reused;
        }
        int index = allocatedSlots++;
        int block = index / SLOTS_PER_BLOCK;
        if (block == blocks.size()) {
            blocks.add(createBlock());
        }
        return new ObjectUniformSlot(blocks.get(block), (long) (index % SLOTS_PER_BLOCK) * slotBytes);
    }

    void release(ObjectUniformSlot slot) {
        freeSlots.add(slot);
    }

    void write(ObjectUniformSlot slot, ByteBuffer data, long transformHash) {
        if (!slot.needsWrite(transformHash)) {
            return;
        }
        backend.writeBuffer(slot.buffer(), data, slot.byteOffset());
        slot.markWritten(transformHash, ringSlots());
    }

    int blockCount() {
        return blocks.size();
    }

    private int ringSlots() {
        return ringBuffered ? backend.perFrameBufferSlots() : 1;
    }

    private BufferHandle createBlock() {
        ByteBuffer empty = BufferUtils.createByteBuffer(SLOTS_PER_BLOCK * slotBytes);
        return backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, empty, ringBuffered));
    }

    void destroy() {
        for (BufferHandle block : blocks) {
            backend.destroy(block);
        }
        blocks.clear();
        freeSlots.clear();
        allocatedSlots = 0;
    }
}
