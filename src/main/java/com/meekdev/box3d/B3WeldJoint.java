package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

public final class B3WeldJoint extends B3Joint {

    B3WeldJoint(MemorySegment id) {
        super(id);
    }

    // zero hertz is fully rigid, otherwise the weld behaves like a spring
    public void setLinearSpring(float hertz, float dampingRatio) {
        box3d_h.b3WeldJoint_SetLinearHertz(id, hertz);
        box3d_h.b3WeldJoint_SetLinearDampingRatio(id, dampingRatio);
    }

    public void setAngularSpring(float hertz, float dampingRatio) {
        box3d_h.b3WeldJoint_SetAngularHertz(id, hertz);
        box3d_h.b3WeldJoint_SetAngularDampingRatio(id, dampingRatio);
    }
}
