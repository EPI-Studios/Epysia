package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable two-dimensional vector that stores its components as {@code float} values.
 *
 * <p>This type is intended for geometry, movement, interpolation, and other real-valued math
 * operations commonly needed in rendering and gameplay code.
 */
public final class Vector2f {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector2f ZERO = new Vector2f(0.0f, 0.0f);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector2f ONE = new Vector2f(1.0f, 1.0f);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector2f UNIT_X = new Vector2f(1.0f, 0.0f);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector2f UNIT_Y = new Vector2f(0.0f, 1.0f);

    private final float x;
    private final float y;

    /**
     * Creates the zero vector.
     */
    public Vector2f() {
        this(0.0f, 0.0f);
    }

    /**
     * Creates a vector from two components.
     *
     * @param x x component
     * @param y y component
     */
    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Creates a vector from an integer vector.
     *
     * @param other source vector
     */
    public Vector2f(Vector2i other) {
        this(other.getX(), other.getY());
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
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector2f withX(float value) {
        return new Vector2f(value, y);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector2f withY(float value) {
        return new Vector2f(x, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector2f add(Vector2f other) {
        return new Vector2f(x + other.x, y + other.y);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector2f add(float value) {
        return new Vector2f(x + value, y + value);
    }

    /**
     * Adds per-component values.
     *
     * @param dx x increment
     * @param dy y increment
     * @return sum
     */
    public Vector2f add(float dx, float dy) {
        return new Vector2f(x + dx, y + dy);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector2f subtract(Vector2f other) {
        return new Vector2f(x - other.x, y - other.y);
    }

    /**
     * Subtracts the same scalar from every component.
     *
     * @param value scalar to subtract
     * @return difference
     */
    public Vector2f subtract(float value) {
        return new Vector2f(x - value, y - value);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector2f multiply(float scalar) {
        return new Vector2f(x * scalar, y * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector2f multiply(Vector2f other) {
        return new Vector2f(x * other.x, y * other.y);
    }

    /**
     * Divides both components by the same scalar.
     *
     * @param scalar division factor
     * @return scaled vector
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector2f divide(float scalar) {
        if (MathUtils.nearlyZero(scalar)) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector2f(x / scalar, y / scalar);
    }

    /**
     * Divides this vector component-wise by another vector.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector2f divide(Vector2f other) {
        if (MathUtils.nearlyZero(other.x) || MathUtils.nearlyZero(other.y)) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector2f(x / other.x, y / other.y);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public float dot(Vector2f other) {
        return x * other.x + y * other.y;
    }

    /**
     * Returns the 2D cross product magnitude.
     *
     * @param other other vector
     * @return signed z component of the cross product
     */
    public float cross(Vector2f other) {
        return x * other.y - y * other.x;
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
    public float distanceSquared(Vector2f other) {
        return subtract(other).lengthSquared();
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public float distance(Vector2f other) {
        return (float) Math.sqrt(distanceSquared(other));
    }

    /**
     * Returns the Manhattan length.
     *
     * @return sum of absolute components
     */
    public float manhattanLength() {
        return Math.abs(x) + Math.abs(y);
    }

    /**
     * Returns the normalized vector.
     *
     * @return unit vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector2f normalize() {
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
    public Vector2f lerp(Vector2f target, float alpha) {
        return multiply(1.0f - alpha).add(target.multiply(alpha));
    }

    /**
     * Projects this vector onto another vector.
     *
     * @param other projection axis
     * @return projected vector
     * @throws ArithmeticException when {@code other} has zero length
     */
    public Vector2f projectOnto(Vector2f other) {
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
    public Vector2f min(Vector2f other) {
        return new Vector2f(Math.min(x, other.x), Math.min(y, other.y));
    }

    /**
     * Returns the per-component maximum.
     *
     * @param other other vector
     * @return maximum vector
     */
    public Vector2f max(Vector2f other) {
        return new Vector2f(Math.max(x, other.x), Math.max(y, other.y));
    }

    /**
     * Clamps each component independently.
     *
     * @param min lower bounds
     * @param max upper bounds
     * @return clamped vector
     */
    public Vector2f clamp(Vector2f min, Vector2f max) {
        return new Vector2f(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y)
        );
    }

    /**
     * Returns the negated vector.
     *
     * @return negated vector
     */
    public Vector2f negate() {
        return new Vector2f(-x, -y);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector2f abs() {
        return new Vector2f(Math.abs(x), Math.abs(y));
    }

    /**
     * Returns the polar angle in radians.
     *
     * @return angle relative to the positive x axis
     */
    public float angle() {
        return (float) Math.atan2(y, x);
    }

    /**
     * Returns the unsigned angle to another vector in radians.
     *
     * @param other other vector
     * @return angle between the vectors
     * @throws ArithmeticException when one vector has zero length
     */
    public float angleBetween(Vector2f other) {
        float denominator = length() * other.length();
        if (MathUtils.nearlyZero(denominator)) {
            throw new ArithmeticException("Cannot compute an angle with a zero-length vector.");
        }
        float value = MathUtils.clamp(dot(other) / denominator, -1.0f, 1.0f);
        return (float) Math.acos(value);
    }

    /**
     * Returns whether all components are effectively zero.
     *
     * @return {@code true} when the vector is close to zero
     */
    public boolean isZero() {
        return MathUtils.nearlyZero(x) && MathUtils.nearlyZero(y);
    }

    /**
     * Returns the component sum.
     *
     * @return sum of x and y
     */
    public float sum() {
        return x + y;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public float minComponent() {
        return Math.min(x, y);
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public float maxComponent() {
        return Math.max(x, y);
    }

    /**
     * Converts this vector to an integer vector by rounding every component.
     *
     * @return rounded integer vector
     */
    public Vector2i toVector2i() {
        return new Vector2i(Math.round(x), Math.round(y));
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector2f copy() {
        return new Vector2f(x, y);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector2f)) {
            return false;
        }
        Vector2f other = (Vector2f) object;
        return Float.compare(other.x, x) == 0 && Float.compare(other.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Vector2f{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
