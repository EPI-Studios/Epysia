package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3Filter;
import com.meekdev.box3d.ffi.b3SurfaceMaterial;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

// thin handle around a native b3ShapeId
public final class B3Shape {

    private final MemorySegment id;

    B3Shape(MemorySegment id) {
        this.id = id;
    }

    public boolean isValid() {
        return box3d_h.b3Shape_IsValid(id);
    }

    public float friction() {
        return box3d_h.b3Shape_GetFriction(id);
    }

    public void setFriction(float friction) {
        box3d_h.b3Shape_SetFriction(id, friction);
    }

    public float restitution() {
        return box3d_h.b3Shape_GetRestitution(id);
    }

    public void setRestitution(float restitution) {
        box3d_h.b3Shape_SetRestitution(id, restitution);
    }

    public void setDensity(float density) {
        box3d_h.b3Shape_SetDensity(id, density, true);
    }

    public boolean isSensor() {
        return box3d_h.b3Shape_IsSensor(id);
    }

    public void setFilter(long categoryBits, long maskBits) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment filter = box3d_h.b3Shape_GetFilter(temp, id);
            b3Filter.categoryBits(filter, categoryBits);
            b3Filter.maskBits(filter, maskBits);
            box3d_h.b3Shape_SetFilter(id, filter, true);
        }
    }

    // aerodynamic push on this shape, drag along the wind, lift across it
    public void applyWind(Vec3 wind, float drag, float lift, float maxSpeed) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment w = b3Vec3.allocate(temp);
            b3Vec3.x(w, (float) wind.x());
            b3Vec3.y(w, (float) wind.y());
            b3Vec3.z(w, (float) wind.z());
            box3d_h.b3Shape_ApplyWind(id, w, drag, lift, maxSpeed, true);
        }
    }

    /** bodies currently overlapping this sensor shape */
    public java.util.List<B3Body> sensorOverlaps(B3World world) {
        try (Arena temp = Arena.ofConfined()) {
            int capacity = box3d_h.b3Shape_GetSensorCapacity(id);
            if (capacity <= 0) {
                return java.util.List.of();
            }
            MemorySegment visitors = com.meekdev.box3d.ffi.b3ShapeId.allocateArray(capacity, temp);
            int count = box3d_h.b3Shape_GetSensorData(id, visitors, capacity);
            java.util.List<B3Body> bodies = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                B3Body body = world.bodyByKey(world.shapeBodyKey(
                        com.meekdev.box3d.ffi.b3ShapeId.asSlice(visitors, i)));
                if (body != null) {
                    bodies.add(body);
                }
            }
            return bodies;
        }
    }

    public void destroy() {
        box3d_h.b3DestroyShape(id, true);
    }
}
