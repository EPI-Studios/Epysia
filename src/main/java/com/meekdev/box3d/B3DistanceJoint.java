package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

public final class B3DistanceJoint extends B3Joint {

    B3DistanceJoint(MemorySegment id) {
        super(id);
    }

    public float length() {
        return box3d_h.b3DistanceJoint_GetLength(id);
    }

    public void setLength(float length) {
        box3d_h.b3DistanceJoint_SetLength(id, length);
    }

    public float currentLength() {
        return box3d_h.b3DistanceJoint_GetCurrentLength(id);
    }

    public void enableSpring(boolean enable) {
        box3d_h.b3DistanceJoint_EnableSpring(id, enable);
    }

    public void setSpring(float hertz, float dampingRatio) {
        box3d_h.b3DistanceJoint_SetSpringHertz(id, hertz);
        box3d_h.b3DistanceJoint_SetSpringDampingRatio(id, dampingRatio);
    }

    public void setLengthRange(float min, float max) {
        box3d_h.b3DistanceJoint_SetLengthRange(id, min, max);
        box3d_h.b3DistanceJoint_EnableLimit(id, true);
    }
}
