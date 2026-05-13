package fr.epistudio.epysia.utils;

import java.util.Objects;

/**
 * Immutable quaternion that stores its components as {@code float} values.
 *
 * <p>The quaternion is expressed as {@code (x, y, z, w)}, where {@code (x, y, z)} forms the
 * imaginary part and {@code w} is the scalar part.
 */
public final class Quaternionf {

    /**
     * Identity rotation.
     */
    public static final Quaternionf IDENTITY = new Quaternionf(0.0f, 0.0f, 0.0f, 1.0f);

    private final float x;
    private final float y;
    private final float z;
    private final float w;

    /**
     * Creates the identity quaternion.
     */
    public Quaternionf() {
        this(0.0f, 0.0f, 0.0f, 1.0f);
    }

    /**
     * Creates a quaternion from four components.
     *
     * @param x x component of the imaginary part
     * @param y y component of the imaginary part
     * @param z z component of the imaginary part
     * @param w scalar part
     */
    public Quaternionf(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Returns the identity quaternion.
     *
     * @return identity quaternion
     */
    public static Quaternionf identity() {
        return IDENTITY;
    }

    /**
     * Creates a quaternion from a normalized axis and an angle.
     *
     * @param axis rotation axis
     * @param radians rotation angle in radians
     * @return rotation quaternion
     */
    public static Quaternionf fromAxisAngle(Vector3f axis, float radians) {
        Vector3f normalizedAxis = axis.normalize();
        float halfAngle = radians * 0.5f;
        float sin = (float) Math.sin(halfAngle);
        float cos = (float) Math.cos(halfAngle);
        return new Quaternionf(
                normalizedAxis.getX() * sin,
                normalizedAxis.getY() * sin,
                normalizedAxis.getZ() * sin,
                cos
        );
    }

    /**
     * Creates a quaternion from Euler angles.
     *
     * <p>The resulting rotation combines an x-axis rotation, then a y-axis rotation, then a z-axis
     * rotation.
     *
     * @param pitch x-axis rotation in radians
     * @param yaw y-axis rotation in radians
     * @param roll z-axis rotation in radians
     * @return rotation quaternion
     */
    public static Quaternionf fromEuler(float pitch, float yaw, float roll) {
        Quaternionf xRotation = fromAxisAngle(Vector3f.UNIT_X, pitch);
        Quaternionf yRotation = fromAxisAngle(Vector3f.UNIT_Y, yaw);
        Quaternionf zRotation = fromAxisAngle(Vector3f.UNIT_Z, roll);
        return xRotation.multiply(yRotation).multiply(zRotation).normalize();
    }

    /**
     * Creates a quaternion from a 3x3 rotation matrix.
     *
     * @param matrix rotation matrix
     * @return quaternion representing the same rotation
     */
    public static Quaternionf fromMatrix(Matrix3f matrix) {
        float trace = matrix.get(0, 0) + matrix.get(1, 1) + matrix.get(2, 2);

        if (trace > 0.0f) {
            float s = (float) Math.sqrt(trace + 1.0f) * 2.0f;
            return new Quaternionf(
                    (matrix.get(2, 1) - matrix.get(1, 2)) / s,
                    (matrix.get(0, 2) - matrix.get(2, 0)) / s,
                    (matrix.get(1, 0) - matrix.get(0, 1)) / s,
                    0.25f * s
            ).normalize();
        }

        if (matrix.get(0, 0) > matrix.get(1, 1) && matrix.get(0, 0) > matrix.get(2, 2)) {
            float s = (float) Math.sqrt(1.0f + matrix.get(0, 0) - matrix.get(1, 1) - matrix.get(2, 2)) * 2.0f;
            return new Quaternionf(
                    0.25f * s,
                    (matrix.get(0, 1) + matrix.get(1, 0)) / s,
                    (matrix.get(0, 2) + matrix.get(2, 0)) / s,
                    (matrix.get(2, 1) - matrix.get(1, 2)) / s
            ).normalize();
        }

        if (matrix.get(1, 1) > matrix.get(2, 2)) {
            float s = (float) Math.sqrt(1.0f + matrix.get(1, 1) - matrix.get(0, 0) - matrix.get(2, 2)) * 2.0f;
            return new Quaternionf(
                    (matrix.get(0, 1) + matrix.get(1, 0)) / s,
                    0.25f * s,
                    (matrix.get(1, 2) + matrix.get(2, 1)) / s,
                    (matrix.get(0, 2) - matrix.get(2, 0)) / s
            ).normalize();
        }

        float s = (float) Math.sqrt(1.0f + matrix.get(2, 2) - matrix.get(0, 0) - matrix.get(1, 1)) * 2.0f;
        return new Quaternionf(
                (matrix.get(0, 2) + matrix.get(2, 0)) / s,
                (matrix.get(1, 2) + matrix.get(2, 1)) / s,
                0.25f * s,
                (matrix.get(1, 0) - matrix.get(0, 1)) / s
        ).normalize();
    }

    /**
     * Creates a quaternion from the rotational part of a 4x4 matrix.
     *
     * @param matrix rotation matrix
     * @return quaternion representing the same rotation
     */
    public static Quaternionf fromMatrix(Matrix4f matrix) {
        return fromMatrix(new Matrix3f(
                matrix.get(0, 0), matrix.get(0, 1), matrix.get(0, 2),
                matrix.get(1, 0), matrix.get(1, 1), matrix.get(1, 2),
                matrix.get(2, 0), matrix.get(2, 1), matrix.get(2, 2)
        ));
    }

    /**
     * Returns the x component of the imaginary part.
     *
     * @return x component
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the y component of the imaginary part.
     *
     * @return y component
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the z component of the imaginary part.
     *
     * @return z component
     */
    public float getZ() {
        return z;
    }

    /**
     * Returns the scalar part.
     *
     * @return scalar component
     */
    public float getW() {
        return w;
    }

    /**
     * Returns a copy with a different x component.
     *
     * @param value new x component
     * @return updated quaternion
     */
    public Quaternionf withX(float value) {
        return new Quaternionf(value, y, z, w);
    }

    /**
     * Returns a copy with a different y component.
     *
     * @param value new y component
     * @return updated quaternion
     */
    public Quaternionf withY(float value) {
        return new Quaternionf(x, value, z, w);
    }

    /**
     * Returns a copy with a different z component.
     *
     * @param value new z component
     * @return updated quaternion
     */
    public Quaternionf withZ(float value) {
        return new Quaternionf(x, y, value, w);
    }

    /**
     * Returns a copy with a different w component.
     *
     * @param value new w component
     * @return updated quaternion
     */
    public Quaternionf withW(float value) {
        return new Quaternionf(x, y, z, value);
    }

    /**
     * Adds another quaternion.
     *
     * @param other quaternion to add
     * @return sum
     */
    public Quaternionf add(Quaternionf other) {
        return new Quaternionf(x + other.x, y + other.y, z + other.z, w + other.w);
    }

    /**
     * Subtracts another quaternion.
     *
     * @param other quaternion to subtract
     * @return difference
     */
    public Quaternionf subtract(Quaternionf other) {
        return new Quaternionf(x - other.x, y - other.y, z - other.z, w - other.w);
    }

    /**
     * Multiplies all components by the same scalar.
     *
     * @param scalar scale factor
     * @return scaled quaternion
     */
    public Quaternionf multiply(float scalar) {
        return new Quaternionf(x * scalar, y * scalar, z * scalar, w * scalar);
    }

    /**
     * Returns the Hamilton product with another quaternion.
     *
     * @param other right operand
     * @return product quaternion
     */
    public Quaternionf multiply(Quaternionf other) {
        return new Quaternionf(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z
        );
    }

    /**
     * Returns the dot product with another quaternion.
     *
     * @param other other quaternion
     * @return dot product
     */
    public float dot(Quaternionf other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    /**
     * Returns the squared length.
     *
     * @return squared length
     */
    public float lengthSquared() {
        return dot(this);
    }

    /**
     * Returns the length.
     *
     * @return quaternion length
     */
    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    /**
     * Returns the normalized quaternion.
     *
     * @return normalized quaternion
     * @throws ArithmeticException when the quaternion has zero length
     */
    public Quaternionf normalize() {
        float len = length();
        if (MathUtils.nearlyZero(len)) {
            throw new ArithmeticException("Cannot normalize a zero-length quaternion.");
        }
        return multiply(1.0f / len);
    }

    /**
     * Returns the conjugate quaternion.
     *
     * @return conjugate
     */
    public Quaternionf conjugate() {
        return new Quaternionf(-x, -y, -z, w);
    }

    /**
     * Returns the inverse quaternion.
     *
     * @return inverse quaternion
     * @throws ArithmeticException when the quaternion has zero length
     */
    public Quaternionf inverse() {
        float lenSquared = lengthSquared();
        if (MathUtils.nearlyZero(lenSquared)) {
            throw new ArithmeticException("Cannot invert a zero-length quaternion.");
        }
        return conjugate().multiply(1.0f / lenSquared);
    }

    /**
     * Rotates a 3D vector.
     *
     * @param vector vector to rotate
     * @return rotated vector
     */
    public Vector3f rotate(Vector3f vector) {
        Quaternionf normalized = normalize();
        Vector3f q = new Vector3f(normalized.x, normalized.y, normalized.z);
        float scalar = normalized.w;

        Vector3f term1 = q.multiply(2.0f * q.dot(vector));
        Vector3f term2 = vector.multiply(scalar * scalar - q.dot(q));
        Vector3f term3 = q.cross(vector).multiply(2.0f * scalar);
        return term1.add(term2).add(term3);
    }

    /**
     * Converts this quaternion to a 3x3 rotation matrix.
     *
     * @return rotation matrix
     */
    public Matrix3f toMatrix3f() {
        Quaternionf normalized = normalize();
        float xx = normalized.x * normalized.x;
        float yy = normalized.y * normalized.y;
        float zz = normalized.z * normalized.z;
        float xy = normalized.x * normalized.y;
        float xz = normalized.x * normalized.z;
        float yz = normalized.y * normalized.z;
        float wx = normalized.w * normalized.x;
        float wy = normalized.w * normalized.y;
        float wz = normalized.w * normalized.z;

        return new Matrix3f(
                1.0f - 2.0f * (yy + zz), 2.0f * (xy - wz), 2.0f * (xz + wy),
                2.0f * (xy + wz), 1.0f - 2.0f * (xx + zz), 2.0f * (yz - wx),
                2.0f * (xz - wy), 2.0f * (yz + wx), 1.0f - 2.0f * (xx + yy)
        );
    }

    /**
     * Converts this quaternion to a 4x4 rotation matrix.
     *
     * @return rotation matrix
     */
    public Matrix4f toMatrix4f() {
        Matrix3f matrix = toMatrix3f();
        return new Matrix4f(
                matrix.get(0, 0), matrix.get(0, 1), matrix.get(0, 2), 0.0f,
                matrix.get(1, 0), matrix.get(1, 1), matrix.get(1, 2), 0.0f,
                matrix.get(2, 0), matrix.get(2, 1), matrix.get(2, 2), 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Linearly interpolates toward another quaternion.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolated quaternion
     */
    public Quaternionf lerp(Quaternionf target, float alpha) {
        return multiply(1.0f - alpha).add(target.multiply(alpha));
    }

    /**
     * Linearly interpolates toward another quaternion and normalizes the result.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return normalized interpolation result
     */
    public Quaternionf nlerp(Quaternionf target, float alpha) {
        Quaternionf adjustedTarget = dot(target) < 0.0f ? target.negate() : target;
        return lerp(adjustedTarget, alpha).normalize();
    }

    /**
     * Spherically interpolates toward another quaternion.
     *
     * @param target interpolation target
     * @param alpha interpolation factor, usually in {@code [0, 1]}
     * @return interpolation result
     */
    public Quaternionf slerp(Quaternionf target, float alpha) {
        Quaternionf adjustedTarget = target;
        float dotProduct = dot(target);

        if (dotProduct < 0.0f) {
            adjustedTarget = target.negate();
            dotProduct = -dotProduct;
        }

        if (dotProduct > 0.9995f) {
            return lerp(adjustedTarget, alpha).normalize();
        }

        float theta0 = (float) Math.acos(MathUtils.clamp(dotProduct, -1.0f, 1.0f));
        float theta = theta0 * alpha;
        float sinTheta = (float) Math.sin(theta);
        float sinTheta0 = (float) Math.sin(theta0);

        float scale0 = (float) Math.cos(theta) - dotProduct * sinTheta / sinTheta0;
        float scale1 = sinTheta / sinTheta0;
        return multiply(scale0).add(adjustedTarget.multiply(scale1)).normalize();
    }

    /**
     * Returns the unsigned rotation angle in radians.
     *
     * @return angle in radians
     */
    public float getAngle() {
        Quaternionf normalized = normalize();
        return 2.0f * (float) Math.acos(MathUtils.clamp(normalized.w, -1.0f, 1.0f));
    }

    /**
     * Returns the normalized rotation axis.
     *
     * @return rotation axis
     */
    public Vector3f getAxis() {
        Quaternionf normalized = normalize();
        float sinHalf = (float) Math.sqrt(1.0f - normalized.w * normalized.w);
        if (MathUtils.nearlyZero(sinHalf)) {
            return Vector3f.UNIT_X;
        }
        return new Vector3f(
                normalized.x / sinHalf,
                normalized.y / sinHalf,
                normalized.z / sinHalf
        );
    }

    /**
     * Returns whether all components are effectively zero.
     *
     * @return {@code true} when the quaternion is close to zero
     */
    public boolean isZero() {
        return MathUtils.nearlyZero(x) && MathUtils.nearlyZero(y)
                && MathUtils.nearlyZero(z) && MathUtils.nearlyZero(w);
    }

    /**
     * Returns the component-wise negated quaternion.
     *
     * @return negated quaternion
     */
    public Quaternionf negate() {
        return new Quaternionf(-x, -y, -z, -w);
    }

    /**
     * Returns the angular distance to another quaternion in radians.
     *
     * @param other other quaternion
     * @return angle between both rotations
     */
    public float angleBetween(Quaternionf other) {
        Quaternionf delta = inverse().multiply(other).normalize();
        return delta.getAngle();
    }

    /**
     * Returns a defensive copy.
     *
     * @return copy of this quaternion
     */
    public Quaternionf copy() {
        return new Quaternionf(x, y, z, w);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Quaternionf)) {
            return false;
        }
        Quaternionf other = (Quaternionf) object;
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
        return "Quaternionf{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", w=" + w +
                '}';
    }
}
