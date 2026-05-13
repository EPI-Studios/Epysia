package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable three-dimensional vector that stores its components as {@code int} values.
 *
 * <p>This type is suited for discrete coordinates, chunk math, voxel/grid work, and other
 * integer-based calculations.
 */
public final class Vector3i {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector3i ZERO = new Vector3i(0, 0, 0);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector3i ONE = new Vector3i(1, 1, 1);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector3i UNIT_X = new Vector3i(1, 0, 0);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector3i UNIT_Y = new Vector3i(0, 1, 0);

    /**
     * Unit vector on the Z axis.
     */
    public static final Vector3i UNIT_Z = new Vector3i(0, 0, 1);

    private final int x;
    private final int y;
    private final int z;

    /**
     * Creates the zero vector.
     */
    public Vector3i() {
        this(0, 0, 0);
    }

    /**
     * Creates a vector from three components.
     *
     * @param x x component
     * @param y y component
     * @param z z component
     */
    public Vector3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates an integer vector by rounding a floating-point vector.
     *
     * @param other source vector
     */
    public Vector3i(Vector3f other) {
        this(Math.round(other.getX()), Math.round(other.getY()), Math.round(other.getZ()));
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
     * Returns the xy part of this vector.
     *
     * @return 2D slice
     */
    public Vector2i xy() {
        return new Vector2i(x, y);
    }

    /**
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector3i withX(int value) {
        return new Vector3i(value, y, z);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector3i withY(int value) {
        return new Vector3i(x, value, z);
    }

    /**
     * Returns a copy with a different z component.
     *
     * @param value new z component
     * @return updated vector
     */
    public Vector3i withZ(int value) {
        return new Vector3i(x, y, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector3i add(Vector3i other) {
        return new Vector3i(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector3i add(int value) {
        return new Vector3i(x + value, y + value, z + value);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector3i subtract(Vector3i other) {
        return new Vector3i(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector3i multiply(int scalar) {
        return new Vector3i(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector3i multiply(Vector3i other) {
        return new Vector3i(x * other.x, y * other.y, z * other.z);
    }

    /**
     * Divides both components by the same scalar using integer division.
     *
     * @param scalar divisor
     * @return quotient
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector3i divide(int scalar) {
        if (scalar == 0) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector3i(x / scalar, y / scalar, z / scalar);
    }

    /**
     * Divides this vector component-wise by another vector using integer division.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector3i divide(Vector3i other) {
        if (other.x == 0 || other.y == 0 || other.z == 0) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector3i(x / other.x, y / other.y, z / other.z);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public long dot(Vector3i other) {
        return (long) x * other.x + (long) y * other.y + (long) z * other.z;
    }

    /**
     * Returns the cross product with another vector.
     *
     * @param other other vector
     * @return cross product
     */
    public Vector3i cross(Vector3i other) {
        return new Vector3i(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
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
     * Returns the Manhattan length.
     *
     * @return sum of absolute components
     */
    public int manhattanLength() {
        return Math.abs(x) + Math.abs(y) + Math.abs(z);
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public double distance(Vector3i other) {
        return subtract(other).length();
    }

    /**
     * Returns the Manhattan distance to another vector.
     *
     * @param other other vector
     * @return Manhattan distance
     */
    public int manhattanDistance(Vector3i other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    /**
     * Returns the normalized vector in floating-point form.
     *
     * @return normalized floating-point vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector3f normalize() {
        double len = length();
        if (len == 0.0d) {
            throw new ArithmeticException("Cannot normalize a zero-length vector.");
        }
        return new Vector3f((float) (x / len), (float) (y / len), (float) (z / len));
    }

    /**
     * Linearly interpolates toward another vector in floating-point form.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolated vector
     */
    public Vector3f lerp(Vector3i target, float alpha) {
        return toVector3f().lerp(target.toVector3f(), alpha);
    }

    /**
     * Projects this vector onto another vector in floating-point form.
     *
     * @param other projection axis
     * @return projected vector
     */
    public Vector3f projectOnto(Vector3i other) {
        return toVector3f().projectOnto(other.toVector3f());
    }

    /**
     * Returns the per-component minimum.
     *
     * @param other other vector
     * @return minimum vector
     */
    public Vector3i min(Vector3i other) {
        return new Vector3i(
                Math.min(x, other.x),
                Math.min(y, other.y),
                Math.min(z, other.z)
        );
    }

    /**
     * Returns the per-component maximum.
     *
     * @param other other vector
     * @return maximum vector
     */
    public Vector3i max(Vector3i other) {
        return new Vector3i(
                Math.max(x, other.x),
                Math.max(y, other.y),
                Math.max(z, other.z)
        );
    }

    /**
     * Clamps each component independently.
     *
     * @param min lower bounds
     * @param max upper bounds
     * @return clamped vector
     */
    public Vector3i clamp(Vector3i min, Vector3i max) {
        return new Vector3i(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y),
                MathUtils.clamp(z, min.z, max.z)
        );
    }

    /**
     * Returns the negated vector.
     *
     * @return negated vector
     */
    public Vector3i negate() {
        return new Vector3i(-x, -y, -z);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector3i abs() {
        return new Vector3i(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    /**
     * Returns whether all components are zero.
     *
     * @return {@code true} when every component is zero
     */
    public boolean isZero() {
        return x == 0 && y == 0 && z == 0;
    }

    /**
     * Returns the component sum.
     *
     * @return sum of x, y, and z
     */
    public int sum() {
        return x + y + z;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public int minComponent() {
        return Math.min(x, Math.min(y, z));
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public int maxComponent() {
        return Math.max(x, Math.max(y, z));
    }

    /**
     * Converts this vector to a floating-point vector.
     *
     * @return floating-point vector
     */
    public Vector3f toVector3f() {
        return new Vector3f(x, y, z);
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector3i copy() {
        return new Vector3i(x, y, z);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector3i)) {
            return false;
        }
        Vector3i other = (Vector3i) object;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Vector3i{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}
