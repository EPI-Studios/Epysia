package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// keeps two frames rotationally aligned, translation stays free
public final class B3ParallelJoint extends B3Joint {

    B3ParallelJoint(MemorySegment id) {
        super(id);
    }

    public void setSpring(float hertz, float dampingRatio) {
        box3d_h.b3ParallelJoint_SetSpringHertz(id, hertz);
        box3d_h.b3ParallelJoint_SetSpringDampingRatio(id, dampingRatio);
    }

    public void setMaxTorque(float torque) {
        box3d_h.b3ParallelJoint_SetMaxTorque(id, torque);
    }
}
