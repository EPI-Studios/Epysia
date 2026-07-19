package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3HeightFieldDef;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** baked height grid, static bodies only, materials are one byte per cell */
public final class B3Heightfield implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment data;
    private boolean closed;

    private B3Heightfield(Arena arena, MemorySegment data) {
        this.arena = arena;
        this.data = data;
    }

    public static final byte HOLE = (byte) 0xFF;

    // heights laid out x major, count = countX * countZ, scale spaces the grid
    public static B3Heightfield bake(float[] heights, int countX, int countZ, Vec3 scale) {
        return bake(heights, null, countX, countZ, scale);
    }

    // materials are one byte per cell, HOLE cuts that cell out entirely
    public static B3Heightfield bake(float[] heights, byte[] materials, int countX, int countZ, Vec3 scale) {
        if (heights.length != countX * countZ) {
            throw new IllegalArgumentException("heights must be countX * countZ");
        }
        NativeLoader.load();

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float h : heights) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }

        Arena arena = Arena.ofConfined();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = temp.allocate(b3HeightFieldDef.layout());
            b3HeightFieldDef.heights(def, temp.allocateFrom(ValueLayout.JAVA_FLOAT, heights));
            b3HeightFieldDef.materialIndices(def, materials == null ? MemorySegment.NULL
                    : temp.allocateFrom(ValueLayout.JAVA_BYTE, materials));
            MemorySegment scaleSeg = b3HeightFieldDef.scale(def);
            b3Vec3.x(scaleSeg, (float) scale.x());
            b3Vec3.y(scaleSeg, (float) scale.y());
            b3Vec3.z(scaleSeg, (float) scale.z());
            b3HeightFieldDef.countX(def, countX);
            b3HeightFieldDef.countZ(def, countZ);
            b3HeightFieldDef.globalMinimumHeight(def, min);
            b3HeightFieldDef.globalMaximumHeight(def, max == min ? min + 1 : max);
            b3HeightFieldDef.clockwiseWinding(def, false);

            MemorySegment data = box3d_h.b3CreateHeightField(def);
            if (data.equals(MemorySegment.NULL)) {
                arena.close();
                throw new IllegalArgumentException("height field bake failed");
            }
            return new B3Heightfield(arena, data);
        }
    }

    MemorySegment data() {
        if (closed) {
            throw new IllegalStateException("height field already destroyed");
        }
        return data;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        box3d_h.b3DestroyHeightField(data);
        arena.close();
        closed = true;
    }
}
