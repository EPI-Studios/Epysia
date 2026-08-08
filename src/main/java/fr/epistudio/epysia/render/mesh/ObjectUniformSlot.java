package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BufferHandle;

final class ObjectUniformSlot {
    private final BufferHandle buffer;
    private final long byteOffset;
    private long transformHash;
    private boolean written;
    private int slotsPending;

    ObjectUniformSlot(BufferHandle buffer, long byteOffset) {
        this.buffer = buffer;
        this.byteOffset = byteOffset;
    }

    BufferHandle buffer() {
        return buffer;
    }

    long byteOffset() {
        return byteOffset;
    }

    boolean needsWrite(long candidateHash) {
        return !written || transformHash != candidateHash || slotsPending > 0;
    }

    void markWritten(long candidateHash, int ringSlots) {
        slotsPending = written && transformHash == candidateHash ? slotsPending - 1 : ringSlots - 1;
        transformHash = candidateHash;
        written = true;
    }

    void reset() {
        transformHash = 0L;
        written = false;
        slotsPending = 0;
    }
}
