package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/** captures a world's steps and queries for deterministic replay */
public final class B3Recording implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed;

    private B3Recording(MemorySegment handle) {
        this.handle = handle;
    }

    public static B3Recording create() {
        NativeLoader.load();
        return new B3Recording(box3d_h.b3CreateRecording(0));
    }

    MemorySegment handle() {
        return handle;
    }

    public boolean save(String path) {
        try (Arena temp = Arena.ofConfined()) {
            return box3d_h.b3SaveRecordingToFile(handle, temp.allocateFrom(path));
        }
    }

    public int size() {
        return box3d_h.b3Recording_GetSize(handle);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        box3d_h.b3DestroyRecording(handle);
        closed = true;
    }
}
