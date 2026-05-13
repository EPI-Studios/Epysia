package fr.epistudio.epysia.utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable 3x3 matrix stored in row-major order.
 *
 * <p>This type is primarily useful for 2D affine transforms expressed in homogeneous coordinates
 * and for general 3x3 linear algebra.
 */
public final class Matrix3f {

    /**
     * Identity matrix.
     */
    public static final Matrix3f IDENTITY = new Matrix3f(
            1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 1.0f
    );

    /**
     * Zero matrix.
     */
    public static final Matrix3f ZERO = new Matrix3f(
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f
    );

    private final float[] values;

    /**
     * Creates the identity matrix.
     */
    public Matrix3f() {
        this(IDENTITY.values);
    }

    /**
     * Creates a matrix from row-major values.
     *
     * @param m00 row 0, column 0
     * @param m01 row 0, column 1
     * @param m02 row 0, column 2
     * @param m10 row 1, column 0
     * @param m11 row 1, column 1
     * @param m12 row 1, column 2
     * @param m20 row 2, column 0
     * @param m21 row 2, column 1
     * @param m22 row 2, column 2
     */
    public Matrix3f(
            float m00, float m01, float m02,
            float m10, float m11, float m12,
            float m20, float m21, float m22
    ) {
        this.values = new float[]{
                m00, m01, m02,
                m10, m11, m12,
                m20, m21, m22
        };
    }

    /**
     * Creates a matrix from a row-major array.
     *
     * @param values row-major array with nine elements
     * @throws IllegalArgumentException when the array length is not 9
     */
    public Matrix3f(float[] values) {
        if (values.length != 9) {
            throw new IllegalArgumentException("A 3x3 matrix requires exactly 9 values.");
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    /**
     * Returns the identity matrix.
     *
     * @return identity matrix
     */
    public static Matrix3f identity() {
        return IDENTITY;
    }

    /**
     * Returns the zero matrix.
     *
     * @return zero matrix
     */
    public static Matrix3f zero() {
        return ZERO;
    }

    /**
     * Builds a 2D translation matrix.
     *
     * @param translation translation vector
     * @return translation matrix
     */
    public static Matrix3f translation(Vector2f translation) {
        return new Matrix3f(
                1.0f, 0.0f, translation.getX(),
                0.0f, 1.0f, translation.getY(),
                0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a 2D rotation matrix.
     *
     * @param radians rotation angle in radians
     * @return rotation matrix
     */
    public static Matrix3f rotation(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Matrix3f(
                cos, -sin, 0.0f,
                sin, cos, 0.0f,
                0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a 2D scale matrix.
     *
     * @param scale scale vector
     * @return scale matrix
     */
    public static Matrix3f scale(Vector2f scale) {
        return new Matrix3f(
                scale.getX(), 0.0f, 0.0f,
                0.0f, scale.getY(), 0.0f,
                0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a 2D transform matrix from translation, rotation, and scale.
     *
     * @param translation translation vector
     * @param radians rotation angle in radians
     * @param scale scale vector
     * @return combined transform matrix
     */
    public static Matrix3f trs(Vector2f translation, float radians, Vector2f scale) {
        return translation(translation).multiply(rotation(radians)).multiply(scale(scale));
    }

    /**
     * Returns a matrix element.
     *
     * @param row zero-based row index
     * @param column zero-based column index
     * @return matrix value
     * @throws IndexOutOfBoundsException when the indices are outside the matrix
     */
    public float get(int row, int column) {
        checkIndex(row, column, 3);
        return values[row * 3 + column];
    }

    /**
     * Returns one row as a vector.
     *
     * @param row zero-based row index
     * @return row vector
     */
    public Vector3f getRow(int row) {
        return new Vector3f(get(row, 0), get(row, 1), get(row, 2));
    }

    /**
     * Returns one column as a vector.
     *
     * @param column zero-based column index
     * @return column vector
     */
    public Vector3f getColumn(int column) {
        return new Vector3f(get(0, column), get(1, column), get(2, column));
    }

    /**
     * Returns a row-major copy of the internal values.
     *
     * @return copied array
     */
    public float[] toArray() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * Adds another matrix.
     *
     * @param other matrix to add
     * @return sum
     */
    public Matrix3f add(Matrix3f other) {
        float[] result = new float[9];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] + other.values[index];
        }
        return new Matrix3f(result);
    }

    /**
     * Subtracts another matrix.
     *
     * @param other matrix to subtract
     * @return difference
     */
    public Matrix3f subtract(Matrix3f other) {
        float[] result = new float[9];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] - other.values[index];
        }
        return new Matrix3f(result);
    }

    /**
     * Multiplies every element by the same scalar.
     *
     * @param scalar scale factor
     * @return scaled matrix
     */
    public Matrix3f multiply(float scalar) {
        float[] result = new float[9];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] * scalar;
        }
        return new Matrix3f(result);
    }

    /**
     * Multiplies this matrix by another matrix.
     *
     * @param other right operand
     * @return product
     */
    public Matrix3f multiply(Matrix3f other) {
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                float sum = 0.0f;
                for (int k = 0; k < 3; k++) {
                    sum += get(row, k) * other.get(k, column);
                }
                result[row * 3 + column] = sum;
            }
        }
        return new Matrix3f(result);
    }

    /**
     * Multiplies this matrix by a vector.
     *
     * @param vector vector operand
     * @return transformed vector
     */
    public Vector3f multiply(Vector3f vector) {
        return new Vector3f(
                get(0, 0) * vector.getX() + get(0, 1) * vector.getY() + get(0, 2) * vector.getZ(),
                get(1, 0) * vector.getX() + get(1, 1) * vector.getY() + get(1, 2) * vector.getZ(),
                get(2, 0) * vector.getX() + get(2, 1) * vector.getY() + get(2, 2) * vector.getZ()
        );
    }

    /**
     * Transforms a 2D point using homogeneous coordinates.
     *
     * @param point point to transform
     * @return transformed point
     */
    public Vector2f transformPoint(Vector2f point) {
        Vector3f result = multiply(new Vector3f(point, 1.0f));
        if (MathUtils.nearlyZero(result.getZ())) {
            return result.xy();
        }
        return new Vector2f(result.getX() / result.getZ(), result.getY() / result.getZ());
    }

    /**
     * Transforms a 2D direction and ignores translation.
     *
     * @param direction direction to transform
     * @return transformed direction
     */
    public Vector2f transformDirection(Vector2f direction) {
        Vector3f result = multiply(new Vector3f(direction, 0.0f));
        return result.xy();
    }

    /**
     * Returns the matrix transpose.
     *
     * @return transposed matrix
     */
    public Matrix3f transpose() {
        return new Matrix3f(
                get(0, 0), get(1, 0), get(2, 0),
                get(0, 1), get(1, 1), get(2, 1),
                get(0, 2), get(1, 2), get(2, 2)
        );
    }

    /**
     * Returns the determinant.
     *
     * @return determinant
     */
    public float determinant() {
        float m00 = get(0, 0);
        float m01 = get(0, 1);
        float m02 = get(0, 2);
        float m10 = get(1, 0);
        float m11 = get(1, 1);
        float m12 = get(1, 2);
        float m20 = get(2, 0);
        float m21 = get(2, 1);
        float m22 = get(2, 2);
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * Returns the matrix inverse.
     *
     * @return inverse matrix
     * @throws ArithmeticException when the matrix is singular
     */
    public Matrix3f inverse() {
        float det = determinant();
        if (MathUtils.nearlyZero(det)) {
            throw new ArithmeticException("Cannot invert a singular matrix.");
        }

        float m00 = get(0, 0);
        float m01 = get(0, 1);
        float m02 = get(0, 2);
        float m10 = get(1, 0);
        float m11 = get(1, 1);
        float m12 = get(1, 2);
        float m20 = get(2, 0);
        float m21 = get(2, 1);
        float m22 = get(2, 2);

        float invDet = 1.0f / det;
        return new Matrix3f(
                (m11 * m22 - m12 * m21) * invDet,
                (m02 * m21 - m01 * m22) * invDet,
                (m01 * m12 - m02 * m11) * invDet,
                (m12 * m20 - m10 * m22) * invDet,
                (m00 * m22 - m02 * m20) * invDet,
                (m02 * m10 - m00 * m12) * invDet,
                (m10 * m21 - m11 * m20) * invDet,
                (m01 * m20 - m00 * m21) * invDet,
                (m00 * m11 - m01 * m10) * invDet
        );
    }

    private static void checkIndex(int row, int column, int size) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IndexOutOfBoundsException("Matrix indices are out of bounds.");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Matrix3f)) {
            return false;
        }
        Matrix3f other = (Matrix3f) object;
        return Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(values));
    }

    @Override
    public String toString() {
        return "Matrix3f{" +
                "values=" + Arrays.toString(values) +
                '}';
    }
}
