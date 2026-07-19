package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// drives the relative velocity between two bodies, good for conveyor and hover things
public final class B3MotorJoint extends B3Joint {

    B3MotorJoint(MemorySegment id) {
        super(id);
    }

    public void setLinearVelocity(Vec3 v) {
        try (var temp = java.lang.foreign.Arena.ofConfined()) {
            MemorySegment seg = com.meekdev.box3d.ffi.b3Vec3.allocate(temp);
            com.meekdev.box3d.ffi.b3Vec3.x(seg, (float) v.x());
            com.meekdev.box3d.ffi.b3Vec3.y(seg, (float) v.y());
            com.meekdev.box3d.ffi.b3Vec3.z(seg, (float) v.z());
            box3d_h.b3MotorJoint_SetLinearVelocity(id, seg);
        }
        wakeBodies();
    }

    public void setMaxVelocityForce(float force) {
        box3d_h.b3MotorJoint_SetMaxVelocityForce(id, force);
    }

    public void setMaxVelocityTorque(float torque) {
        box3d_h.b3MotorJoint_SetMaxVelocityTorque(id, torque);
    }
}
