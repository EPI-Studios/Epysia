package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/** convex hull baked from a point cloud, the go to shape for dynamic 3d models */
public final class B3Hull implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment data;
    private boolean closed;

    private B3Hull(Arena arena, MemorySegment data) {
        this.arena = arena;
        this.data = data;
    }

    // points are xyz triples, box3d simplifies down to maxVertices
    public static B3Hull bake(float[] points, int maxVertices) {
        if (points.length % 3 != 0 || points.length < 12) {
            throw new IllegalArgumentException("need at least four xyz points");
        }
        NativeLoader.load();
        Arena arena = Arena.ofConfined();
        try (Arena temp = Arena.ofConfined()) {
            int count = points.length / 3;
            MemorySegment cloud = b3Vec3.allocateArray(count, temp);
            for (int i = 0; i < count; i++) {
                MemorySegment v = b3Vec3.asSlice(cloud, i);
                b3Vec3.x(v, points[i * 3]);
                b3Vec3.y(v, points[i * 3 + 1]);
                b3Vec3.z(v, points[i * 3 + 2]);
            }
            MemorySegment data = box3d_h.b3CreateHull(cloud, count, maxVertices);
            if (data.equals(MemorySegment.NULL)) {
                arena.close();
                throw new IllegalArgumentException("hull bake failed, points may be degenerate");
            }
            return new B3Hull(arena, data);
        }
    }

    public static B3Hull cylinder(float height, float radius, int sides) {
        NativeLoader.load();
        Arena arena = Arena.ofConfined();
        return new B3Hull(arena, box3d_h.b3CreateCylinder(height, radius, -height * 0.5f, sides));
    }

    public static B3Hull cone(float height, float bottomRadius, float topRadius, int slices) {
        NativeLoader.load();
        Arena arena = Arena.ofConfined();
        return new B3Hull(arena, box3d_h.b3CreateCone(height, bottomRadius, topRadius, slices));
    }

    // a lumpy convex boulder, seeded by the engine
    public static B3Hull rock(float radius) {
        NativeLoader.load();
        Arena arena = Arena.ofConfined();
        return new B3Hull(arena, box3d_h.b3CreateRock(radius));
    }

    // a moved, rotated and scaled copy, the original stays usable
    public B3Hull transformed(Vec3 offset, Quat rotation, Vec3 scale) {
        Arena copyArena = Arena.ofConfined();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment transform = temp.allocate(com.meekdev.box3d.ffi.b3Transform.layout());
            MemorySegment p = com.meekdev.box3d.ffi.b3Transform.p(transform);
            b3Vec3.x(p, (float) offset.x());
            b3Vec3.y(p, (float) offset.y());
            b3Vec3.z(p, (float) offset.z());
            MemorySegment q = com.meekdev.box3d.ffi.b3Transform.q(transform);
            MemorySegment v = com.meekdev.box3d.ffi.b3Quat.v(q);
            b3Vec3.x(v, rotation.x());
            b3Vec3.y(v, rotation.y());
            b3Vec3.z(v, rotation.z());
            com.meekdev.box3d.ffi.b3Quat.s(q, rotation.s());
            MemorySegment scaleSeg = b3Vec3.allocate(temp);
            b3Vec3.x(scaleSeg, (float) scale.x());
            b3Vec3.y(scaleSeg, (float) scale.y());
            b3Vec3.z(scaleSeg, (float) scale.z());
            return new B3Hull(copyArena, box3d_h.b3CloneAndTransformHull(data(), transform, scaleSeg));
        }
    }

    public B3Hull scaled(double factor) {
        return transformed(Vec3.zero, Quat.identity, new Vec3(factor, factor, factor));
    }

    MemorySegment data() {
        if (closed) {
            throw new IllegalStateException("hull already destroyed");
        }
        return data;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        box3d_h.b3DestroyHull(data);
        arena.close();
        closed = true;
    }
}
