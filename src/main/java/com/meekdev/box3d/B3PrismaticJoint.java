package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// slider along the frame axis
public final class B3PrismaticJoint extends B3Joint {

    B3PrismaticJoint(MemorySegment id) {
        super(id);
    }

    public float translation() {
        return box3d_h.b3PrismaticJoint_GetTranslation(id);
    }

    public void setLimits(float lower, float upper) {
        box3d_h.b3PrismaticJoint_SetLimits(id, lower, upper);
        box3d_h.b3PrismaticJoint_EnableLimit(id, true);
    }

    public void setMotor(float speed, float maxForce) {
        box3d_h.b3PrismaticJoint_SetMotorSpeed(id, speed);
        box3d_h.b3PrismaticJoint_SetMaxMotorForce(id, maxForce);
        box3d_h.b3PrismaticJoint_EnableMotor(id, true);
        wakeBodies();
    }

    public void setSpring(float hertz, float dampingRatio, float targetTranslation) {
        box3d_h.b3PrismaticJoint_SetSpringHertz(id, hertz);
        box3d_h.b3PrismaticJoint_SetSpringDampingRatio(id, dampingRatio);
        box3d_h.b3PrismaticJoint_SetTargetTranslation(id, targetTranslation);
        box3d_h.b3PrismaticJoint_EnableSpring(id, true);
        wakeBodies();
    }
}
