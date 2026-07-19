package com.meekdev.box3d;

import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.MemorySegment;

// handle around a native b3JointId, typed accessors live on the subclasses
public class B3Joint {

    final MemorySegment id;

    B3Joint(MemorySegment id) {
        this.id = id;
    }

    public boolean isValid() {
        return box3d_h.b3Joint_IsValid(id);
    }

    public void wakeBodies() {
        box3d_h.b3Joint_WakeBodies(id);
    }

    public void destroy() {
        box3d_h.b3DestroyJoint(id, true);
    }
}
