package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable four-dimensional vector that stores its components as {@code int} values.
 *
 * <p>This type is suited for integer tuples, packed coordinates, and other discrete 4D data.
 */
public final class Vector4i {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector4i ZERO = new Vector4i(0, 0, 0, 0);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector4i ONE = new Vector4i(1, 1, 1, 1);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector4i UNIT_X = new Vector4i(1, 0, 0, 0);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector4i UNIT_Y = new Vector4i(0, 1, 0, 0);

    /**
     * Unit vector on the Z axis.
     */
    public static final Vector4i UNIT_Z = new Vector4i(0, 0, 1, 0);

    /**
     * Unit vector on the W axis.
     */
    public static final Vector4i UNIT_W = new Vector4i(0, 0, 0, 1);

    private final int x;
    private final int y;
    private final int z;
    private final int w;

    /**
     * Creates the zero vector.
     */
    public Vector4i() {
        this(0, 0, 0, 0);
    }

    /**
     * Creates a vector from four components.
     *
     * @param x x component
     * @param y y component
     * @param z z component
     * @param w w component
     */
    public Vector4i(int x, int y, int z, int w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Creates an integer vector by rounding a floating-point vector.
     *
     * @param other source vector
     */
    public Vector4i(Vector4f other) {
        this(
                Math.round(other.getX()),
                Math.round(other.getY()),
                Math.round(other.getZ()),
                Math.round(other.getW())
        );
    }

    /**
     * Returns the x component.
     *
     * @return x component
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the y component.
     *
     * @return y component
     */
    public int getY() {
        return y;
    }

    /**
     * Returns the z component.
     *
     * @return z component
     */
    public int getZ() {
        return z;
    }

    /**
     * Returns the w component.
     *
     * @return w component
     */
    public int getW() {
        return w;
    }

    /**
     * Returns the xyz part of this vector.
     *
     * @return 3D slice
     */
    public Vector3i xyz() {
        return new Vector3i(x, y, z);
    }

    /**
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector4i withX(int value) {
        return new Vector4i(value, y, z, w);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector4i withY(int value) {
        return new Vector4i(x, value, z, w);
    }

    /**
     * Returns a copy with a different z component.
     *
     * @param value new z component
     * @return updated vector
     */
    public Vector4i withZ(int value) {
        return new Vector4i(x, y, value, w);
    }

    /**
     * Returns a copy with a different w component.
     *
     * @param value new w component
     * @return updated vector
     */
    public Vector4i withW(int value) {
        return new Vector4i(x, y, z, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector4i add(Vector4i other) {
        return new Vector4i(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector4i add(int value) {
        return new Vector4i(x + value, y + value, z + value, w + value);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector4i subtract(Vector4i other) {
        return new Vector4i(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector4i multiply(int scalar) {
        return new Vector4i(x * scalar, y * scalar, z * scalar, w * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector4i multiply(Vector4i other) {
        return new Vector4i(x * other.x, y * other.y, z * other.z, w * other.w);
    }

    /**
     * Divides both components by the same scalar using integer division.
     *
     * @param scalar divisor
     * @return quotient
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector4i divide(int scalar) {
        if (scalar == 0) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector4i(x / scalar, y / scalar, z / scalar, w / scalar);
    }

    /**
     * Divides this vector component-wise by another vector using integer division.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector4i divide(Vector4i other) {
        if (other.x == 0 || other.y == 0 || other.z == 0 || other.w == 0) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector4i(x / other.x, y / other.y, z / other.z, w / other.w);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public long dot(Vector4i other) {
        return (long) x * other.x + (long) y * other.y + (long) z * other.z + (long) w * other.w;
    }

    /**
     * Returns the squared Euclidean length.
     *
     * @return squared length
     */
    public long lengthSquared() {
        return dot(this);
    }

    /**
     * Returns the Euclidean length.
     *
     * @return vector length
     */
    public double length() {
        return Math.sqrt(lengthSquared());
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public double distance(Vector4i other) {
        return subtract(other).length();
    }

    /**
     * Returns the normalized vector in floating-point form.
     *
     * @return normalized floating-point vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector4f normalize() {
        double len = length();
        if (len == 0.0d) {
            throw new ArithmeticException("Cannot normalize a zero-length vector.");
        }
        return new Vector4f((float) (x / len), (float) (y / len), (float) (z / len), (float) (w / len));
    }

    /**
     * Linearly interpolates toward another vector in floating-point form.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolated vector
     */
    public Vector4f lerp(Vector4i target, float alpha) {
        return toVector4f().lerp(target.toVector4f(), alpha);
    }

    /**
     * Projects this vector onto another vector in floating-point form.
     *
     * @param other projection axis
     * @return projected vector
     */
    public Vector4f projectOnto(Vector4i other) {
        return toVector4f().projectOnto(other.toVector4f());
    }

    /**
     * Returns the per-component minimum.
     *
     * @param other other vector
     * @return minimum vector
     */
    public Vector4i min(Vector4i other) {
        return new Vector4i(
                Math.min(x, other.x),
                Math.min(y, other.y),
                Math.min(z, other.z),
                Math.min(w, other.w)
        );
    }

    /**
     * Returns the per-component maximum.
     *
     * @param other other vector
     * @return maximum vector
     */
    public Vector4i max(Vector4i other) {
        return new Vector4i(
                Math.max(x, other.x),
                Math.max(y, other.y),
                Math.max(z, other.z),
                Math.max(w, other.w)
        );
    }

    /**
     * Clamps each component independently.
     *
     * @param min lower bounds
     * @param max upper bounds
     * @return clamped vector
     */
    public Vector4i clamp(Vector4i min, Vector4i max) {
        return new Vector4i(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y),
                MathUtils.clamp(z, min.z, max.z),
                MathUtils.clamp(w, min.w, max.w)
        );
    }

    /**
     * Returns the negated vector.
     *
     * @return negated vector
     */
    public Vector4i negate() {
        return new Vector4i(-x, -y, -z, -w);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector4i abs() {
        return new Vector4i(Math.abs(x), Math.abs(y), Math.abs(z), Math.abs(w));
    }

    /**
     * Returns whether all components are zero.
     *
     * @return {@code true} when every component is zero
     */
    public boolean isZero() {
        return x == 0 && y == 0 && z == 0 && w == 0;
    }

    /**
     * Returns the component sum.
     *
     * @return sum of all components
     */
    public int sum() {
        return x + y + z + w;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public int minComponent() {
        return Math.min(Math.min(x, y), Math.min(z, w));
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public int maxComponent() {
        return Math.max(Math.max(x, y), Math.max(z, w));
    }

    /**
     * Converts this vector to a floating-point vector.
     *
     * @return floating-point vector
     */
    public Vector4f toVector4f() {
        return new Vector4f(x, y, z, w);
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector4i copy() {
        return new Vector4i(x, y, z, w);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector4i)) {
            return false;
        }
        Vector4i other = (Vector4i) object;
        return x == other.x && y == other.y && z == other.z && w == other.w;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, w);
    }

    @Override
    public String toString() {
        return "Vector4i{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", w=" + w +
                '}';
    }
}
