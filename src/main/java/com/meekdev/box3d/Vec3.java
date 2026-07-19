package com.meekdev.box3d;

// shared math type for the wrapper, converted to float b3Vec3 or double b3Pos at the ffi boundary
public record Vec3(double x, double y, double z) {

    public static final Vec3 zero = new Vec3(0, 0, 0);

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
