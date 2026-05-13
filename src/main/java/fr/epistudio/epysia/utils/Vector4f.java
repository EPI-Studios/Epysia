package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable four-dimensional vector that stores its components as {@code float} values.
 *
 * <p>This type is commonly used for homogeneous coordinates, colors, clip-space values, and other
 * 4D math operations.
 */
public final class Vector4f {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector4f ZERO = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector4f ONE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector4f UNIT_X = new Vector4f(1.0f, 0.0f, 0.0f, 0.0f);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector4f UNIT_Y = new Vector4f(0.0f, 1.0f, 0.0f, 0.0f);

    /**
     * Unit vector on the Z axis.
     */
    public static final Vector4f UNIT_Z = new Vector4f(0.0f, 0.0f, 1.0f, 0.0f);

    /**
     * Unit vector on the W axis.
     */
    public static final Vector4f UNIT_W = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    private final float x;
    private final float y;
    private final float z;
    private final float w;

    /**
     * Creates the zero vector.
     */
    public Vector4f() {
        this(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Creates a vector from four components.
     *
     * @param x x component
     * @param y y component
     * @param z z component
     * @param w w component
     */
    public Vector4f(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Creates a vector from a 3D vector and a w component.
     *
     * @param xyz xyz components
     * @param w w component
     */
    public Vector4f(Vector3f xyz, float w) {
        this(xyz.getX(), xyz.getY(), xyz.getZ(), w);
    }

    /**
     * Creates a vector from an integer vector.
     *
     * @param other source vector
     */
    public Vector4f(Vector4i other) {
        this(other.getX(), other.getY(), other.getZ(), other.getW());
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
     * Returns the w component.
     *
     * @return w component
     */
    public float getW() {
        return w;
    }

    /**
     * Returns the xyz part of this vector.
     *
     * @return 3D slice
     */
    public Vector3f xyz() {
        return new Vector3f(x, y, z);
    }

    /**
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector4f withX(float value) {
        return new Vector4f(value, y, z, w);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector4f withY(float value) {
        return new Vector4f(x, value, z, w);
    }

    /**
     * Returns a copy with a different z component.
     *
     * @param value new z component
     * @return updated vector
     */
    public Vector4f withZ(float value) {
        return new Vector4f(x, y, value, w);
    }

    /**
     * Returns a copy with a different w component.
     *
     * @param value new w component
     * @return updated vector
     */
    public Vector4f withW(float value) {
        return new Vector4f(x, y, z, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector4f add(Vector4f other) {
        return new Vector4f(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector4f add(float value) {
        return new Vector4f(x + value, y + value, z + value, w + value);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector4f subtract(Vector4f other) {
        return new Vector4f(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector4f multiply(float scalar) {
        return new Vector4f(x * scalar, y * scalar, z * scalar, w * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector4f multiply(Vector4f other) {
        return new Vector4f(x * other.x, y * other.y, z * other.z, w * other.w);
    }

    /**
     * Divides both components by the same scalar.
     *
     * @param scalar division factor
     * @return scaled vector
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector4f divide(float scalar) {
        if (MathUtils.nearlyZero(scalar)) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector4f(x / scalar, y / scalar, z / scalar, w / scalar);
    }

    /**
     * Divides this vector component-wise by another vector.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector4f divide(Vector4f other) {
        if (MathUtils.nearlyZero(other.x) || MathUtils.nearlyZero(other.y)
                || MathUtils.nearlyZero(other.z) || MathUtils.nearlyZero(other.w)) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector4f(x / other.x, y / other.y, z / other.z, w / other.w);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public float dot(Vector4f other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
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
    public float distanceSquared(Vector4f other) {
        return subtract(other).lengthSquared();
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public float distance(Vector4f other) {
        return (float) Math.sqrt(distanceSquared(other));
    }

    /**
     * Returns the normalized vector.
     *
     * @return unit vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector4f normalize() {
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
    public Vector4f lerp(Vector4f target, float alpha) {
        return multiply(1.0f - alpha).add(target.multiply(alpha));
    }

    /**
     * Projects this vector onto another vector.
     *
     * @param other projection axis
     * @return projected vector
     * @throws ArithmeticException when {@code other} has zero length
     */
    public Vector4f projectOnto(Vector4f other) {
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
    public Vector4f min(Vector4f other) {
        return new Vector4f(
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
    public Vector4f max(Vector4f other) {
        return new Vector4f(
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
    public Vector4f clamp(Vector4f min, Vector4f max) {
        return new Vector4f(
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
    public Vector4f negate() {
        return new Vector4f(-x, -y, -z, -w);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector4f abs() {
        return new Vector4f(Math.abs(x), Math.abs(y), Math.abs(z), Math.abs(w));
    }

    /**
     * Returns the unsigned angle to another vector in radians.
     *
     * @param other other vector
     * @return angle between the vectors
     * @throws ArithmeticException when one vector has zero length
     */
    public float angleBetween(Vector4f other) {
        float denominator = length() * other.length();
        if (MathUtils.nearlyZero(denominator)) {
            throw new ArithmeticException("Cannot compute an angle with a zero-length vector.");
        }
        float value = MathUtils.clamp(dot(other) / denominator, -1.0f, 1.0f);
        return (float) Math.acos(value);
    }

    /**
     * Converts homogeneous coordinates back to 3D.
     *
     * @return divided 3D vector
     * @throws ArithmeticException when {@code w} is zero
     */
    public Vector3f perspectiveDivide() {
        if (MathUtils.nearlyZero(w)) {
            throw new ArithmeticException("Cannot perform perspective divide with w equal to zero.");
        }
        return new Vector3f(x / w, y / w, z / w);
    }

    /**
     * Returns whether all components are effectively zero.
     *
     * @return {@code true} when the vector is close to zero
     */
    public boolean isZero() {
        return MathUtils.nearlyZero(x) && MathUtils.nearlyZero(y)
                && MathUtils.nearlyZero(z) && MathUtils.nearlyZero(w);
    }

    /**
     * Returns the component sum.
     *
     * @return sum of all components
     */
    public float sum() {
        return x + y + z + w;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public float minComponent() {
        return Math.min(Math.min(x, y), Math.min(z, w));
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public float maxComponent() {
        return Math.max(Math.max(x, y), Math.max(z, w));
    }

    /**
     * Converts this vector to an integer vector by rounding every component.
     *
     * @return rounded integer vector
     */
    public Vector4i toVector4i() {
        return new Vector4i(Math.round(x), Math.round(y), Math.round(z), Math.round(w));
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector4f copy() {
        return new Vector4f(x, y, z, w);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector4f)) {
            return false;
        }
        Vector4f other = (Vector4f) object;
        return Float.compare(other.x, x) == 0
                && Float.compare(other.y, y) == 0
                && Float.compare(other.z, z) == 0
                && Float.compare(other.w, w) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, w);
    }

    @Override
    public String toString() {
        return "Vector4f{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", w=" + w +
                '}';
    }
}
