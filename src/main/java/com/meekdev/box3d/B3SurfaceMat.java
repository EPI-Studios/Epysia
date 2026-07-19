package com.meekdev.box3d;

/** per material surface response, referenced by mesh material indices */
public record B3SurfaceMat(float friction, float restitution, float rollingResistance) {

    public static B3SurfaceMat of(float friction) {
        return new B3SurfaceMat(friction, 0f, 0f);
    }
}
