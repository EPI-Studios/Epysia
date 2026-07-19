package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3BoxHull;
import com.meekdev.box3d.ffi.b3CompoundDef;
import com.meekdev.box3d.ffi.b3CompoundHullDef;
import com.meekdev.box3d.ffi.b3CompoundSphereDef;
import com.meekdev.box3d.ffi.b3Quat;
import com.meekdev.box3d.ffi.b3Sphere;
import com.meekdev.box3d.ffi.b3Transform;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/** several primitives fused into one shape, static bodies only, box3d's rule */
public final class B3Compound implements AutoCloseable {

    private record BoxPart(Vec3 center, Vec3 half, Quat rot) {}

    private record SpherePart(Vec3 center, float radius) {}

    private final List<BoxPart> boxes = new ArrayList<>();
    private final List<SpherePart> spheres = new ArrayList<>();
    private Arena arena;
    private MemorySegment data;

    public B3Compound addBox(Vec3 center, Vec3 halfExtents) {
        return addBox(center, halfExtents, Quat.identity);
    }

    public B3Compound addBox(Vec3 center, Vec3 halfExtents, Quat rotation) {
        checkMutable();
        boxes.add(new BoxPart(center, halfExtents, rotation));
        return this;
    }

    public B3Compound addSphere(Vec3 center, float radius) {
        checkMutable();
        spheres.add(new SpherePart(center, radius));
        return this;
    }

    public B3Compound bake() {
        checkMutable();
        if (boxes.isEmpty() && spheres.isEmpty()) {
            throw new IllegalStateException("empty compound");
        }
        NativeLoader.load();
        arena = Arena.ofConfined();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = temp.allocate(b3CompoundDef.layout());

            if (!boxes.isEmpty()) {
                MemorySegment hulls = b3CompoundHullDef.allocateArray(boxes.size(), temp);
                for (int i = 0; i < boxes.size(); i++) {
                    BoxPart part = boxes.get(i);
                    MemorySegment hull = box3d_h.b3MakeBoxHull(temp,
                            (float) part.half.x(), (float) part.half.y(), (float) part.half.z());
                    MemorySegment entry = b3CompoundHullDef.asSlice(hulls, i);
                    b3CompoundHullDef.hull(entry, b3BoxHull.base(hull));
                    MemorySegment transform = b3CompoundHullDef.transform(entry);
                    MemorySegment p = b3Transform.p(transform);
                    b3Vec3.x(p, (float) part.center.x());
                    b3Vec3.y(p, (float) part.center.y());
                    b3Vec3.z(p, (float) part.center.z());
                    MemorySegment q = b3Transform.q(transform);
                    MemorySegment v = b3Quat.v(q);
                    b3Vec3.x(v, part.rot.x());
                    b3Vec3.y(v, part.rot.y());
                    b3Vec3.z(v, part.rot.z());
                    b3Quat.s(q, part.rot.s());
                    b3CompoundHullDef.material(entry, box3d_h.b3DefaultSurfaceMaterial(temp));
                }
                b3CompoundDef.hulls(def, hulls);
                b3CompoundDef.hullCount(def, boxes.size());
            }

            if (!spheres.isEmpty()) {
                MemorySegment sphereDefs = b3CompoundSphereDef.allocateArray(spheres.size(), temp);
                for (int i = 0; i < spheres.size(); i++) {
                    SpherePart part = spheres.get(i);
                    MemorySegment entry = b3CompoundSphereDef.asSlice(sphereDefs, i);
                    MemorySegment sphere = b3CompoundSphereDef.sphere(entry);
                    MemorySegment center = b3Sphere.center(sphere);
                    b3Vec3.x(center, (float) part.center.x());
                    b3Vec3.y(center, (float) part.center.y());
                    b3Vec3.z(center, (float) part.center.z());
                    b3Sphere.radius(sphere, part.radius);
                    b3CompoundSphereDef.material(entry, box3d_h.b3DefaultSurfaceMaterial(temp));
                }
                b3CompoundDef.spheres(def, sphereDefs);
                b3CompoundDef.sphereCount(def, spheres.size());
            }

            data = box3d_h.b3CreateCompound(def);
            if (data.equals(MemorySegment.NULL)) {
                arena.close();
                arena = null;
                throw new IllegalArgumentException("compound bake failed");
            }
        }
        return this;
    }

    MemorySegment data() {
        if (data == null) {
            throw new IllegalStateException("compound not baked");
        }
        return data;
    }

    private void checkMutable() {
        if (data != null) {
            throw new IllegalStateException("compound already baked");
        }
    }

    @Override
    public void close() {
        if (data != null) {
            box3d_h.b3DestroyCompound(data);
            arena.close();
            data = null;
        }
    }
}
