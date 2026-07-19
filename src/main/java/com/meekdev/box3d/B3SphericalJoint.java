package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// ball and socket
public final class B3SphericalJoint extends B3Joint {

    B3SphericalJoint(MemorySegment id) {
        super(id);
    }

    public void setConeLimit(float radians) {
        box3d_h.b3SphericalJoint_SetConeLimit(id, radians);
        box3d_h.b3SphericalJoint_EnableConeLimit(id, true);
    }

    public void setTwistLimits(float lower, float upper) {
        box3d_h.b3SphericalJoint_SetTwistLimits(id, lower, upper);
        box3d_h.b3SphericalJoint_EnableTwistLimit(id, true);
    }

    public void setSpring(float hertz, float dampingRatio) {
        box3d_h.b3SphericalJoint_SetSpringHertz(id, hertz);
        box3d_h.b3SphericalJoint_SetSpringDampingRatio(id, dampingRatio);
        box3d_h.b3SphericalJoint_EnableSpring(id, true);
    }
}
