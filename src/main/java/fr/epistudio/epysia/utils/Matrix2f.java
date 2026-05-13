package fr.epistudio.epysia.utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable 2x2 matrix stored in row-major order.
 *
 * <p>This type is useful for compact linear transformations in 2D, such as rotation and scaling
 * without translation.
 */
public final class Matrix2f {

    /**
     * Identity matrix.
     */
    public static final Matrix2f IDENTITY = new Matrix2f(
            1.0f, 0.0f,
            0.0f, 1.0f
    );

    /**
     * Zero matrix.
     */
    public static final Matrix2f ZERO = new Matrix2f(
            0.0f, 0.0f,
            0.0f, 0.0f
    );

    private final float[] values;

    /**
     * Creates the identity matrix.
     */
    public Matrix2f() {
        this(IDENTITY.values);
    }

    /**
     * Creates a matrix from row-major values.
     *
     * @param m00 row 0, column 0
     * @param m01 row 0, column 1
     * @param m10 row 1, column 0
     * @param m11 row 1, column 1
     */
    public Matrix2f(float m00, float m01, float m10, float m11) {
        this.values = new float[]{m00, m01, m10, m11};
    }

    /**
     * Creates a matrix from a row-major array.
     *
     * @param values row-major array with four elements
     * @throws IllegalArgumentException when the array length is not 4
     */
    public Matrix2f(float[] values) {
        if (values.length != 4) {
            throw new IllegalArgumentException("A 2x2 matrix requires exactly 4 values.");
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    /**
     * Returns the identity matrix.
     *
     * @return identity matrix
     */
    public static Matrix2f identity() {
        return IDENTITY;
    }

    /**
     * Returns the zero matrix.
     *
     * @return zero matrix
     */
    public static Matrix2f zero() {
        return ZERO;
    }

    /**
     * Builds a 2D rotation matrix.
     *
     * @param radians rotation angle in radians
     * @return rotation matrix
     */
    public static Matrix2f rotation(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Matrix2f(cos, -sin, sin, cos);
    }

    /**
     * Builds a 2D scale matrix.
     *
     * @param scale scale vector
     * @return scale matrix
     */
    public static Matrix2f scale(Vector2f scale) {
        return new Matrix2f(scale.getX(), 0.0f, 0.0f, scale.getY());
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
        checkIndex(row, column, 2);
        return values[row * 2 + column];
    }

    /**
     * Returns one row as a vector.
     *
     * @param row zero-based row index
     * @return row vector
     */
    public Vector2f getRow(int row) {
        return new Vector2f(get(row, 0), get(row, 1));
    }

    /**
     * Returns one column as a vector.
     *
     * @param column zero-based column index
     * @return column vector
     */
    public Vector2f getColumn(int column) {
        return new Vector2f(get(0, column), get(1, column));
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
    public Matrix2f add(Matrix2f other) {
        return new Matrix2f(
                values[0] + other.values[0],
                values[1] + other.values[1],
                values[2] + other.values[2],
                values[3] + other.values[3]
        );
    }

    /**
     * Subtracts another matrix.
     *
     * @param other matrix to subtract
     * @return difference
     */
    public Matrix2f subtract(Matrix2f other) {
        return new Matrix2f(
                values[0] - other.values[0],
                values[1] - other.values[1],
                values[2] - other.values[2],
                values[3] - other.values[3]
        );
    }

    /**
     * Multiplies every element by the same scalar.
     *
     * @param scalar scale factor
     * @return scaled matrix
     */
    public Matrix2f multiply(float scalar) {
        return new Matrix2f(
                values[0] * scalar,
                values[1] * scalar,
                values[2] * scalar,
                values[3] * scalar
        );
    }

    /**
     * Multiplies this matrix by another matrix.
     *
     * @param other right operand
     * @return product
     */
    public Matrix2f multiply(Matrix2f other) {
        return new Matrix2f(
                get(0, 0) * other.get(0, 0) + get(0, 1) * other.get(1, 0),
                get(0, 0) * other.get(0, 1) + get(0, 1) * other.get(1, 1),
                get(1, 0) * other.get(0, 0) + get(1, 1) * other.get(1, 0),
                get(1, 0) * other.get(0, 1) + get(1, 1) * other.get(1, 1)
        );
    }

    /**
     * Multiplies this matrix by a vector.
     *
     * @param vector vector operand
     * @return transformed vector
     */
    public Vector2f multiply(Vector2f vector) {
        return new Vector2f(
                get(0, 0) * vector.getX() + get(0, 1) * vector.getY(),
                get(1, 0) * vector.getX() + get(1, 1) * vector.getY()
        );
    }

    /**
     * Returns the matrix transpose.
     *
     * @return transposed matrix
     */
    public Matrix2f transpose() {
        return new Matrix2f(
                get(0, 0), get(1, 0),
                get(0, 1), get(1, 1)
        );
    }

    /**
     * Returns the determinant.
     *
     * @return determinant
     */
    public float determinant() {
        return get(0, 0) * get(1, 1) - get(0, 1) * get(1, 0);
    }

    /**
     * Returns the matrix inverse.
     *
     * @return inverse matrix
     * @throws ArithmeticException when the matrix is singular
     */
    public Matrix2f inverse() {
        float det = determinant();
        if (MathUtils.nearlyZero(det)) {
            throw new ArithmeticException("Cannot invert a singular matrix.");
        }
        float inverseDeterminant = 1.0f / det;
        return new Matrix2f(
                get(1, 1) * inverseDeterminant,
                -get(0, 1) * inverseDeterminant,
                -get(1, 0) * inverseDeterminant,
                get(0, 0) * inverseDeterminant
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
        if (!(object instanceof Matrix2f)) {
            return false;
        }
        Matrix2f other = (Matrix2f) object;
        return Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(values));
    }

    @Override
    public String toString() {
        return "Matrix2f{" +
                "values=" + Arrays.toString(values) +
                '}';
    }
}
