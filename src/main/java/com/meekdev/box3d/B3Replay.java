package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** replays a recording in its own world and checks determinism along the way */
public final class B3Replay implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment player;

    private B3Replay(Arena arena, MemorySegment player) {
        this.arena = arena;
        this.player = player;
    }

    public static B3Replay of(B3Recording recording) {
        NativeLoader.load();
        Arena arena = Arena.ofConfined();
        int size = recording.size();
        MemorySegment data = box3d_h.b3Recording_GetData(recording.handle()).reinterpret(size);
        MemorySegment player = box3d_h.b3RecPlayer_Create(data, size, 1);
        return wrap(arena, player);
    }

    public static B3Replay fromFile(Path path) throws IOException {
        NativeLoader.load();
        byte[] bytes = Files.readAllBytes(path);
        Arena arena = Arena.ofConfined();
        MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
        MemorySegment player = box3d_h.b3RecPlayer_Create(data, bytes.length, 1);
        return wrap(arena, player);
    }

    private static B3Replay wrap(Arena arena, MemorySegment player) {
        if (player.equals(MemorySegment.NULL)) {
            arena.close();
            throw new IllegalArgumentException("bad recording data");
        }
        return new B3Replay(arena, player);
    }

    // false once the end is reached
    public boolean stepFrame() {
        return box3d_h.b3RecPlayer_StepFrame(player);
    }

    public int frameCount() {
        return box3d_h.b3RecPlayer_GetFrameCount(player);
    }

    public boolean hasDiverged() {
        return box3d_h.b3RecPlayer_HasDiverged(player);
    }

    public void restart() {
        box3d_h.b3RecPlayer_Restart(player);
    }

    @Override
    public void close() {
        box3d_h.b3RecPlayer_Destroy(player);
        arena.close();
    }
}
