package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable three-dimensional vector that stores its components as {@code float} values.
 *
 * <p>This type is intended for 3D positions, directions, normals, and general real-valued vector
 * math.
 */
public final class Vector3f {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector3f ZERO = new Vector3f(0.0f, 0.0f, 0.0f);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector3f ONE = new Vector3f(1.0f, 1.0f, 1.0f);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector3f UNIT_X = new Vector3f(1.0f, 0.0f, 0.0f);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector3f UNIT_Y = new Vector3f(0.0f, 1.0f, 0.0f);

    /**
     * Unit vector on the Z axis.
     */
    public static final Vector3f UNIT_Z = new Vector3f(0.0f, 0.0f, 1.0f);

    private final float x;
    private final float y;
    private final float z;

    /**
     * Creates the zero vector.
     */
    public Vector3f() {
        this(0.0f, 0.0f, 0.0f);
    }

    /**
     * Creates a vector from three components.
     *
     * @param x x component
     * @param y y component
     * @param z z component
     */
    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a vector from a 2D vector and a z component.
     *
     * @param xy x and y components
     * @param z z component
     */
    public Vector3f(Vector2f xy, float z) {
        this(xy.getX(), xy.getY(), z);
    }

    /**
     * Creates a vector from an integer vector.
     *
     * @param other source vector
     */
    public Vector3f(Vector3i other) {
        this(other.getX(), other.getY(), other.getZ());
    }

    /**
     * Returns the x component.
     *
     * @return x component
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the y component.
     *
     * @return y component
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the z component.
     *
     * @return z component
     */
    public float getZ() {
        return z;
    }

    /**
     * Returns the xy part of this vector.
     *
     * @return 2D slice
     */
    public Vector2f xy() {
        return new Vector2f(x, y);
    }

    /**
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector3f withX(float value) {
        return new Vector3f(value, y, z);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector3f withY(float value) {
        return new Vector3f(x, value, z);
    }

    /**
     * Returns a copy with a different z component.
     *
     * @param value new z component
     * @return updated vector
     */
    public Vector3f withZ(float value) {
        return new Vector3f(x, y, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector3f add(Vector3f other) {
        return new Vector3f(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector3f add(float value) {
        return new Vector3f(x + value, y + value, z + value);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector3f subtract(Vector3f other) {
        return new Vector3f(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector3f multiply(float scalar) {
        return new Vector3f(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector3f multiply(Vector3f other) {
        return new Vector3f(x * other.x, y * other.y, z * other.z);
    }

    /**
     * Divides both components by the same scalar.
     *
     * @param scalar division factor
     * @return scaled vector
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector3f divide(float scalar) {
        if (MathUtils.nearlyZero(scalar)) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector3f(x / scalar, y / scalar, z / scalar);
    }

    /**
     * Divides this vector component-wise by another vector.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector3f divide(Vector3f other) {
        if (MathUtils.nearlyZero(other.x) || MathUtils.nearlyZero(other.y) || MathUtils.nearlyZero(other.z)) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector3f(x / other.x, y / other.y, z / other.z);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public float dot(Vector3f other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Returns the cross product with another vector.
     *
     * @param other other vector
     * @return cross product
     */
    public Vector3f cross(Vector3f other) {
        return new Vector3f(
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
    public float lengthSquared() {
        return dot(this);
    }

    /**
     * Returns the Euclidean length.
     *
     * @return vector length
     */
    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    /**
     * Returns the squared distance to another vector.
     *
     * @param other other vector
     * @return squared distance
     */
    public float distanceSquared(Vector3f other) {
        return subtract(other).lengthSquared();
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public float distance(Vector3f other) {
        return (float) Math.sqrt(distanceSquared(other));
    }

    /**
     * Returns the Manhattan length.
     *
     * @return sum of absolute components
     */
    public float manhattanLength() {
        return Math.abs(x) + Math.abs(y) + Math.abs(z);
    }

    /**
     * Returns the normalized vector.
     *
     * @return unit vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector3f normalize() {
        float len = length();
        if (MathUtils.nearlyZero(len)) {
            throw new ArithmeticException("Cannot normalize a zero-length vector.");
        }
        return divide(len);
    }

    /**
     * Linearly interpolates toward another vector.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolated vector
     */
    public Vector3f lerp(Vector3f target, float alpha) {
        return multiply(1.0f - alpha).add(target.multiply(alpha));
    }

    /**
     * Projects this vector onto another vector.
     *
     * @param other projection axis
     * @return projected vector
     * @throws ArithmeticException when {@code other} has zero length
     */
    public Vector3f projectOnto(Vector3f other) {
        float denominator = other.lengthSquared();
        if (MathUtils.nearlyZero(denominator)) {
            throw new ArithmeticException("Cannot project onto a zero-length vector.");
        }
        return other.multiply(dot(other) / denominator);
    }

    /**
     * Returns the per-component minimum.
     *
     * @param other other vector
     * @return minimum vector
     */
    public Vector3f min(Vector3f other) {
        return new Vector3f(
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
    public Vector3f max(Vector3f other) {
        return new Vector3f(
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
    public Vector3f clamp(Vector3f min, Vector3f max) {
        return new Vector3f(
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
    public Vector3f negate() {
        return new Vector3f(-x, -y, -z);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector3f abs() {
        return new Vector3f(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    /**
     * Returns the unsigned angle to another vector in radians.
     *
     * @param other other vector
     * @return angle between the vectors
     * @throws ArithmeticException when one vector has zero length
     */
    public float angleBetween(Vector3f other) {
        float denominator = length() * other.length();
        if (MathUtils.nearlyZero(denominator)) {
            throw new ArithmeticException("Cannot compute an angle with a zero-length vector.");
        }
        float value = MathUtils.clamp(dot(other) / denominator, -1.0f, 1.0f);
        return (float) Math.acos(value);
    }

    /**
     * Rotates the vector with a quaternion.
     *
     * @param rotation quaternion rotation
     * @return rotated vector
     */
    public Vector3f rotate(Quaternionf rotation) {
        return rotation.rotate(this);
    }

    /**
     * Returns whether all components are effectively zero.
     *
     * @return {@code true} when the vector is close to zero
     */
    public boolean isZero() {
        return MathUtils.nearlyZero(x) && MathUtils.nearlyZero(y) && MathUtils.nearlyZero(z);
    }

    /**
     * Returns the component sum.
     *
     * @return sum of x, y, and z
     */
    public float sum() {
        return x + y + z;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public float minComponent() {
        return Math.min(x, Math.min(y, z));
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public float maxComponent() {
        return Math.max(x, Math.max(y, z));
    }

    /**
     * Converts this vector to an integer vector by rounding every component.
     *
     * @return rounded integer vector
     */
    public Vector3i toVector3i() {
        return new Vector3i(Math.round(x), Math.round(y), Math.round(z));
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector3f copy() {
        return new Vector3f(x, y, z);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector3f)) {
            return false;
        }
        Vector3f other = (Vector3f) object;
        return Float.compare(other.x, x) == 0
                && Float.compare(other.y, y) == 0
                && Float.compare(other.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Vector3f{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}
