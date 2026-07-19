package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

public final class B3RevoluteJoint extends B3Joint {

    B3RevoluteJoint(MemorySegment id) {
        super(id);
    }

    public float angle() {
        return box3d_h.b3RevoluteJoint_GetAngle(id);
    }

    public void setLimits(float lower, float upper) {
        box3d_h.b3RevoluteJoint_SetLimits(id, lower, upper);
        box3d_h.b3RevoluteJoint_EnableLimit(id, true);
    }

    public void setMotor(float speed, float maxTorque) {
        box3d_h.b3RevoluteJoint_SetMotorSpeed(id, speed);
        box3d_h.b3RevoluteJoint_SetMaxMotorTorque(id, maxTorque);
        box3d_h.b3RevoluteJoint_EnableMotor(id, true);
        wakeBodies();
    }

    public float motorTorque() {
        return box3d_h.b3RevoluteJoint_GetMotorTorque(id);
    }
}
