package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable two-dimensional vector that stores its components as {@code int} values.
 *
 * <p>This type is suited for grid coordinates, array indexing, tile math, and other discrete
 * calculations.
 */
public final class Vector2i {

    /**
     * Vector with all components set to {@code 0}.
     */
    public static final Vector2i ZERO = new Vector2i(0, 0);

    /**
     * Vector with all components set to {@code 1}.
     */
    public static final Vector2i ONE = new Vector2i(1, 1);

    /**
     * Unit vector on the X axis.
     */
    public static final Vector2i UNIT_X = new Vector2i(1, 0);

    /**
     * Unit vector on the Y axis.
     */
    public static final Vector2i UNIT_Y = new Vector2i(0, 1);

    private final int x;
    private final int y;

    /**
     * Creates the zero vector.
     */
    public Vector2i() {
        this(0, 0);
    }

    /**
     * Creates a vector from two components.
     *
     * @param x x component
     * @param y y component
     */
    public Vector2i(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Creates an integer vector by rounding a floating-point vector.
     *
     * @param other source vector
     */
    public Vector2i(Vector2f other) {
        this(Math.round(other.getX()), Math.round(other.getY()));
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
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated vector
     */
    public Vector2i withX(int value) {
        return new Vector2i(value, y);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated vector
     */
    public Vector2i withY(int value) {
        return new Vector2i(x, value);
    }

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return sum
     */
    public Vector2i add(Vector2i other) {
        return new Vector2i(x + other.x, y + other.y);
    }

    /**
     * Adds the same scalar to every component.
     *
     * @param value scalar to add
     * @return sum
     */
    public Vector2i add(int value) {
        return new Vector2i(x + value, y + value);
    }

    /**
     * Subtracts another vector.
     *
     * @param other vector to subtract
     * @return difference
     */
    public Vector2i subtract(Vector2i other) {
        return new Vector2i(x - other.x, y - other.y);
    }

    /**
     * Multiplies both components by the same scalar.
     *
     * @param scalar multiplication factor
     * @return scaled vector
     */
    public Vector2i multiply(int scalar) {
        return new Vector2i(x * scalar, y * scalar);
    }

    /**
     * Multiplies this vector component-wise with another vector.
     *
     * @param other other vector
     * @return component-wise product
     */
    public Vector2i multiply(Vector2i other) {
        return new Vector2i(x * other.x, y * other.y);
    }

    /**
     * Divides both components by the same scalar using integer division.
     *
     * @param scalar divisor
     * @return quotient
     * @throws ArithmeticException when {@code scalar} is zero
     */
    public Vector2i divide(int scalar) {
        if (scalar == 0) {
            throw new ArithmeticException("Cannot divide a vector by zero.");
        }
        return new Vector2i(x / scalar, y / scalar);
    }

    /**
     * Divides this vector component-wise by another vector using integer division.
     *
     * @param other divisor
     * @return component-wise quotient
     * @throws ArithmeticException when one component of {@code other} is zero
     */
    public Vector2i divide(Vector2i other) {
        if (other.x == 0 || other.y == 0) {
            throw new ArithmeticException("Cannot divide a vector by a zero component.");
        }
        return new Vector2i(x / other.x, y / other.y);
    }

    /**
     * Returns the dot product with another vector.
     *
     * @param other other vector
     * @return dot product
     */
    public long dot(Vector2i other) {
        return (long) x * other.x + (long) y * other.y;
    }

    /**
     * Returns the 2D cross product magnitude.
     *
     * @param other other vector
     * @return signed z component of the cross product
     */
    public long cross(Vector2i other) {
        return (long) x * other.y - (long) y * other.x;
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
        return Math.abs(x) + Math.abs(y);
    }

    /**
     * Returns the distance to another vector.
     *
     * @param other other vector
     * @return Euclidean distance
     */
    public double distance(Vector2i other) {
        return subtract(other).length();
    }

    /**
     * Returns the Manhattan distance to another vector.
     *
     * @param other other vector
     * @return Manhattan distance
     */
    public int manhattanDistance(Vector2i other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    /**
     * Returns the normalized vector in floating-point form.
     *
     * @return normalized floating-point vector
     * @throws ArithmeticException when the vector has no direction
     */
    public Vector2f normalize() {
        double len = length();
        if (len == 0.0d) {
            throw new ArithmeticException("Cannot normalize a zero-length vector.");
        }
        return new Vector2f((float) (x / len), (float) (y / len));
    }

    /**
     * Linearly interpolates toward another vector in floating-point form.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolated vector
     */
    public Vector2f lerp(Vector2i target, float alpha) {
        return toVector2f().lerp(target.toVector2f(), alpha);
    }

    /**
     * Projects this vector onto another vector in floating-point form.
     *
     * @param other projection axis
     * @return projected vector
     */
    public Vector2f projectOnto(Vector2i other) {
        return toVector2f().projectOnto(other.toVector2f());
    }

    /**
     * Returns the per-component minimum.
     *
     * @param other other vector
     * @return minimum vector
     */
    public Vector2i min(Vector2i other) {
        return new Vector2i(Math.min(x, other.x), Math.min(y, other.y));
    }

    /**
     * Returns the per-component maximum.
     *
     * @param other other vector
     * @return maximum vector
     */
    public Vector2i max(Vector2i other) {
        return new Vector2i(Math.max(x, other.x), Math.max(y, other.y));
    }

    /**
     * Clamps each component independently.
     *
     * @param min lower bounds
     * @param max upper bounds
     * @return clamped vector
     */
    public Vector2i clamp(Vector2i min, Vector2i max) {
        return new Vector2i(
                MathUtils.clamp(x, min.x, max.x),
                MathUtils.clamp(y, min.y, max.y)
        );
    }

    /**
     * Returns the negated vector.
     *
     * @return negated vector
     */
    public Vector2i negate() {
        return new Vector2i(-x, -y);
    }

    /**
     * Returns the vector with absolute component values.
     *
     * @return absolute vector
     */
    public Vector2i abs() {
        return new Vector2i(Math.abs(x), Math.abs(y));
    }

    /**
     * Returns whether all components are zero.
     *
     * @return {@code true} when both components are zero
     */
    public boolean isZero() {
        return x == 0 && y == 0;
    }

    /**
     * Returns the component sum.
     *
     * @return sum of x and y
     */
    public int sum() {
        return x + y;
    }

    /**
     * Returns the smallest component.
     *
     * @return smallest component
     */
    public int minComponent() {
        return Math.min(x, y);
    }

    /**
     * Returns the largest component.
     *
     * @return largest component
     */
    public int maxComponent() {
        return Math.max(x, y);
    }

    /**
     * Converts this vector to a floating-point vector.
     *
     * @return floating-point vector
     */
    public Vector2f toVector2f() {
        return new Vector2f(x, y);
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this vector
     */
    public Vector2i copy() {
        return new Vector2i(x, y);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector2i)) {
            return false;
        }
        Vector2i other = (Vector2i) object;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Vector2i{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
