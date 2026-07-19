package com.meekdev.box3d;

import java.lang.foreign.MemorySegment;

// no constraint at all, just stops two bodies from colliding with each other
public final class B3FilterJoint extends B3Joint {

    B3FilterJoint(MemorySegment id) {
        super(id);
    }
}
