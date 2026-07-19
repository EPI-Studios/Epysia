package com.meekdev.box3d;

// per shape settings, category and mask follow the usual bitfield filtering scheme
public record B3ShapeConfig(float friction, float restitution, float density,
                            long categoryBits, long maskBits, boolean sensor) {

    public static B3ShapeConfig defaults() {
        return new B3ShapeConfig(0.6f, 0f, 1000f, 1L, ~0L, false);
    }

    public B3ShapeConfig withFriction(float value) {
        return new B3ShapeConfig(value, restitution, density, categoryBits, maskBits, sensor);
    }

    public B3ShapeConfig withRestitution(float value) {
        return new B3ShapeConfig(friction, value, density, categoryBits, maskBits, sensor);
    }

    public B3ShapeConfig withDensity(float value) {
        return new B3ShapeConfig(friction, restitution, value, categoryBits, maskBits, sensor);
    }

    public B3ShapeConfig withFilter(long category, long mask) {
        return new B3ShapeConfig(friction, restitution, density, category, mask, sensor);
    }

    public B3ShapeConfig asSensor() {
        return new B3ShapeConfig(friction, restitution, density, categoryBits, maskBits, true);
    }
}
