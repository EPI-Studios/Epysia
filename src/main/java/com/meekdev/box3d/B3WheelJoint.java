package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// wheel with suspension, steering and a spin motor
public final class B3WheelJoint extends B3Joint {

    B3WheelJoint(MemorySegment id) {
        super(id);
    }

    public void setSuspension(float hertz, float dampingRatio, float lower, float upper) {
        box3d_h.b3WheelJoint_SetSuspensionHertz(id, hertz);
        box3d_h.b3WheelJoint_SetSuspensionDampingRatio(id, dampingRatio);
        box3d_h.b3WheelJoint_SetSuspensionLimits(id, lower, upper);
        box3d_h.b3WheelJoint_EnableSuspension(id, true);
        box3d_h.b3WheelJoint_EnableSuspensionLimit(id, true);
    }

    public void setSpinMotor(float speed, float maxTorque) {
        box3d_h.b3WheelJoint_SetSpinMotorSpeed(id, speed);
        box3d_h.b3WheelJoint_SetMaxSpinTorque(id, maxTorque);
        box3d_h.b3WheelJoint_EnableSpinMotor(id, true);
        wakeBodies();
    }

    public void setSteering(float targetAngle, float maxTorque) {
        box3d_h.b3WheelJoint_SetTargetSteeringAngle(id, targetAngle);
        box3d_h.b3WheelJoint_SetMaxSteeringTorque(id, maxTorque);
        box3d_h.b3WheelJoint_EnableSteering(id, true);
        wakeBodies();
    }

    public float spinSpeed() {
        return box3d_h.b3WheelJoint_GetSpinSpeed(id);
    }
}
