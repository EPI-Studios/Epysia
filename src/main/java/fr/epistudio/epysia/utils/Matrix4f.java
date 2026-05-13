package fr.epistudio.epysia.utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable 4x4 matrix stored in row-major order.
 *
 * <p>This type is intended for 3D affine transforms, camera matrices, projections, and general
 * 4x4 linear algebra.
 */
public final class Matrix4f {

    /**
     * Identity matrix.
     */
    public static final Matrix4f IDENTITY = new Matrix4f(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    );

    /**
     * Zero matrix.
     */
    public static final Matrix4f ZERO = new Matrix4f(
            0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f
    );

    private final float[] values;

    /**
     * Creates the identity matrix.
     */
    public Matrix4f() {
        this(IDENTITY.values);
    }

    /**
     * Creates a matrix from row-major values.
     *
     * @param m00 row 0, column 0
     * @param m01 row 0, column 1
     * @param m02 row 0, column 2
     * @param m03 row 0, column 3
     * @param m10 row 1, column 0
     * @param m11 row 1, column 1
     * @param m12 row 1, column 2
     * @param m13 row 1, column 3
     * @param m20 row 2, column 0
     * @param m21 row 2, column 1
     * @param m22 row 2, column 2
     * @param m23 row 2, column 3
     * @param m30 row 3, column 0
     * @param m31 row 3, column 1
     * @param m32 row 3, column 2
     * @param m33 row 3, column 3
     */
    public Matrix4f(
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23,
            float m30, float m31, float m32, float m33
    ) {
        this.values = new float[]{
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33
        };
    }

    /**
     * Creates a matrix from a row-major array.
     *
     * @param values row-major array with sixteen elements
     * @throws IllegalArgumentException when the array length is not 16
     */
    public Matrix4f(float[] values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("A 4x4 matrix requires exactly 16 values.");
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    /**
     * Returns the identity matrix.
     *
     * @return identity matrix
     */
    public static Matrix4f identity() {
        return IDENTITY;
    }

    /**
     * Returns the zero matrix.
     *
     * @return zero matrix
     */
    public static Matrix4f zero() {
        return ZERO;
    }

    /**
     * Builds a translation matrix.
     *
     * @param translation translation vector
     * @return translation matrix
     */
    public static Matrix4f translation(Vector3f translation) {
        return new Matrix4f(
                1.0f, 0.0f, 0.0f, translation.getX(),
                0.0f, 1.0f, 0.0f, translation.getY(),
                0.0f, 0.0f, 1.0f, translation.getZ(),
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a scale matrix.
     *
     * @param scale scale vector
     * @return scale matrix
     */
    public static Matrix4f scale(Vector3f scale) {
        return new Matrix4f(
                scale.getX(), 0.0f, 0.0f, 0.0f,
                0.0f, scale.getY(), 0.0f, 0.0f,
                0.0f, 0.0f, scale.getZ(), 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a rotation matrix around the x axis.
     *
     * @param radians rotation angle in radians
     * @return rotation matrix
     */
    public static Matrix4f rotationX(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Matrix4f(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, cos, -sin, 0.0f,
                0.0f, sin, cos, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a rotation matrix around the y axis.
     *
     * @param radians rotation angle in radians
     * @return rotation matrix
     */
    public static Matrix4f rotationY(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Matrix4f(
                cos, 0.0f, sin, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                -sin, 0.0f, cos, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a rotation matrix around the z axis.
     *
     * @param radians rotation angle in radians
     * @return rotation matrix
     */
    public static Matrix4f rotationZ(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Matrix4f(
                cos, -sin, 0.0f, 0.0f,
                sin, cos, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a rotation matrix from a quaternion.
     *
     * @param rotation quaternion rotation
     * @return rotation matrix
     */
    public static Matrix4f rotation(Quaternionf rotation) {
        return rotation.toMatrix4f();
    }

    /**
     * Builds a transform matrix from translation, rotation, and scale.
     *
     * @param translation translation vector
     * @param rotation rotation quaternion
     * @param scale scale vector
     * @return combined transform matrix
     */
    public static Matrix4f trs(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        return translation(translation).multiply(rotation(rotation)).multiply(scale(scale));
    }

    /**
     * Builds a perspective projection matrix.
     *
     * @param fovY vertical field of view in radians
     * @param aspect viewport aspect ratio
     * @param near near clipping plane
     * @param far far clipping plane
     * @return perspective matrix
     * @throws IllegalArgumentException when the parameters cannot describe a valid projection
     */
    public static Matrix4f perspective(float fovY, float aspect, float near, float far) {
        if (MathUtils.nearlyZero(aspect) || MathUtils.nearlyEquals(near, far)) {
            throw new IllegalArgumentException("Invalid perspective projection parameters.");
        }
        float f = 1.0f / (float) Math.tan(fovY * 0.5f);
        return new Matrix4f(
                f / aspect, 0.0f, 0.0f, 0.0f,
                0.0f, f, 0.0f, 0.0f,
                0.0f, 0.0f, (far + near) / (near - far), (2.0f * far * near) / (near - far),
                0.0f, 0.0f, -1.0f, 0.0f
        );
    }

    /**
     * Builds an orthographic projection matrix.
     *
     * @param left left plane
     * @param right right plane
     * @param bottom bottom plane
     * @param top top plane
     * @param near near plane
     * @param far far plane
     * @return orthographic matrix
     * @throws IllegalArgumentException when the parameters cannot describe a valid projection
     */
    public static Matrix4f orthographic(float left, float right, float bottom, float top, float near, float far) {
        if (MathUtils.nearlyEquals(left, right)
                || MathUtils.nearlyEquals(bottom, top)
                || MathUtils.nearlyEquals(near, far)) {
            throw new IllegalArgumentException("Invalid orthographic projection parameters.");
        }
        return new Matrix4f(
                2.0f / (right - left), 0.0f, 0.0f, -(right + left) / (right - left),
                0.0f, 2.0f / (top - bottom), 0.0f, -(top + bottom) / (top - bottom),
                0.0f, 0.0f, -2.0f / (far - near), -(far + near) / (far - near),
                0.0f, 0.0f, 0.0f, 1.0f
        );
    }

    /**
     * Builds a right-handed view matrix.
     *
     * @param eye camera position
     * @param target look target
     * @param up approximate up direction
     * @return view matrix
     */
    public static Matrix4f lookAt(Vector3f eye, Vector3f target, Vector3f up) {
        Vector3f forward = target.subtract(eye).normalize();
        Vector3f right = forward.cross(up).normalize();
        Vector3f correctedUp = right.cross(forward);

        return new Matrix4f(
                right.getX(), right.getY(), right.getZ(), -right.dot(eye),
                correctedUp.getX(), correctedUp.getY(), correctedUp.getZ(), -correctedUp.dot(eye),
                -forward.getX(), -forward.getY(), -forward.getZ(), forward.dot(eye),
                0.0f, 0.0f, 0.0f, 1.0f
        );
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
        checkIndex(row, column, 4);
        return values[row * 4 + column];
    }

    /**
     * Returns one row as a vector.
     *
     * @param row zero-based row index
     * @return row vector
     */
    public Vector4f getRow(int row) {
        return new Vector4f(get(row, 0), get(row, 1), get(row, 2), get(row, 3));
    }

    /**
     * Returns one column as a vector.
     *
     * @param column zero-based column index
     * @return column vector
     */
    public Vector4f getColumn(int column) {
        return new Vector4f(get(0, column), get(1, column), get(2, column), get(3, column));
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
    public Matrix4f add(Matrix4f other) {
        float[] result = new float[16];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] + other.values[index];
        }
        return new Matrix4f(result);
    }

    /**
     * Subtracts another matrix.
     *
     * @param other matrix to subtract
     * @return difference
     */
    public Matrix4f subtract(Matrix4f other) {
        float[] result = new float[16];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] - other.values[index];
        }
        return new Matrix4f(result);
    }

    /**
     * Multiplies every element by the same scalar.
     *
     * @param scalar scale factor
     * @return scaled matrix
     */
    public Matrix4f multiply(float scalar) {
        float[] result = new float[16];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] * scalar;
        }
        return new Matrix4f(result);
    }

    /**
     * Multiplies this matrix by another matrix.
     *
     * @param other right operand
     * @return product
     */
    public Matrix4f multiply(Matrix4f other) {
        float[] result = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += get(row, k) * other.get(k, column);
                }
                result[row * 4 + column] = sum;
            }
        }
        return new Matrix4f(result);
    }

    /**
     * Multiplies this matrix by a vector.
     *
     * @param vector vector operand
     * @return transformed vector
     */
    public Vector4f multiply(Vector4f vector) {
        return new Vector4f(
                get(0, 0) * vector.getX() + get(0, 1) * vector.getY() + get(0, 2) * vector.getZ() + get(0, 3) * vector.getW(),
                get(1, 0) * vector.getX() + get(1, 1) * vector.getY() + get(1, 2) * vector.getZ() + get(1, 3) * vector.getW(),
                get(2, 0) * vector.getX() + get(2, 1) * vector.getY() + get(2, 2) * vector.getZ() + get(2, 3) * vector.getW(),
                get(3, 0) * vector.getX() + get(3, 1) * vector.getY() + get(3, 2) * vector.getZ() + get(3, 3) * vector.getW()
        );
    }

    /**
     * Transforms a 3D point using homogeneous coordinates.
     *
     * @param point point to transform
     * @return transformed point
     */
    public Vector3f transformPoint(Vector3f point) {
        Vector4f result = multiply(new Vector4f(point, 1.0f));
        if (MathUtils.nearlyZero(result.getW())) {
            return result.xyz();
        }
        return new Vector3f(
                result.getX() / result.getW(),
                result.getY() / result.getW(),
                result.getZ() / result.getW()
        );
    }

    /**
     * Transforms a 3D direction and ignores translation.
     *
     * @param direction direction to transform
     * @return transformed direction
     */
    public Vector3f transformDirection(Vector3f direction) {
        return multiply(new Vector4f(direction, 0.0f)).xyz();
    }

    /**
     * Returns the matrix transpose.
     *
     * @return transposed matrix
     */
    public Matrix4f transpose() {
        return new Matrix4f(
                get(0, 0), get(1, 0), get(2, 0), get(3, 0),
                get(0, 1), get(1, 1), get(2, 1), get(3, 1),
                get(0, 2), get(1, 2), get(2, 2), get(3, 2),
                get(0, 3), get(1, 3), get(2, 3), get(3, 3)
        );
    }

    /**
     * Returns the determinant.
     *
     * @return determinant
     */
    public float determinant() {
        float[][] matrix = toMatrixArray();
        float sign = 1.0f;
        float determinant = 1.0f;

        for (int pivot = 0; pivot < 4; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (Math.abs(matrix[row][pivot]) > Math.abs(matrix[maxRow][pivot])) {
                    maxRow = row;
                }
            }

            if (MathUtils.nearlyZero(matrix[maxRow][pivot])) {
                return 0.0f;
            }

            if (maxRow != pivot) {
                float[] temp = matrix[pivot];
                matrix[pivot] = matrix[maxRow];
                matrix[maxRow] = temp;
                sign *= -1.0f;
            }

            float pivotValue = matrix[pivot][pivot];
            determinant *= pivotValue;

            for (int row = pivot + 1; row < 4; row++) {
                float factor = matrix[row][pivot] / pivotValue;
                for (int column = pivot; column < 4; column++) {
                    matrix[row][column] -= factor * matrix[pivot][column];
                }
            }
        }

        return determinant * sign;
    }

    /**
     * Returns the matrix inverse.
     *
     * @return inverse matrix
     * @throws ArithmeticException when the matrix is singular
     */
    public Matrix4f inverse() {
        float[][] left = toMatrixArray();
        float[][] right = {
                {1.0f, 0.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f, 0.0f},
                {0.0f, 0.0f, 1.0f, 0.0f},
                {0.0f, 0.0f, 0.0f, 1.0f}
        };

        for (int pivot = 0; pivot < 4; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (Math.abs(left[row][pivot]) > Math.abs(left[maxRow][pivot])) {
                    maxRow = row;
                }
            }

            if (MathUtils.nearlyZero(left[maxRow][pivot])) {
                throw new ArithmeticException("Cannot invert a singular matrix.");
            }

            if (maxRow != pivot) {
                swapRows(left, pivot, maxRow);
                swapRows(right, pivot, maxRow);
            }

            float pivotValue = left[pivot][pivot];
            for (int column = 0; column < 4; column++) {
                left[pivot][column] /= pivotValue;
                right[pivot][column] /= pivotValue;
            }

            for (int row = 0; row < 4; row++) {
                if (row == pivot) {
                    continue;
                }
                float factor = left[row][pivot];
                for (int column = 0; column < 4; column++) {
                    left[row][column] -= factor * left[pivot][column];
                    right[row][column] -= factor * right[pivot][column];
                }
            }
        }

        return new Matrix4f(
                right[0][0], right[0][1], right[0][2], right[0][3],
                right[1][0], right[1][1], right[1][2], right[1][3],
                right[2][0], right[2][1], right[2][2], right[2][3],
                right[3][0], right[3][1], right[3][2], right[3][3]
        );
    }

    private float[][] toMatrixArray() {
        return new float[][]{
                {get(0, 0), get(0, 1), get(0, 2), get(0, 3)},
                {get(1, 0), get(1, 1), get(1, 2), get(1, 3)},
                {get(2, 0), get(2, 1), get(2, 2), get(2, 3)},
                {get(3, 0), get(3, 1), get(3, 2), get(3, 3)}
        };
    }

    private static void swapRows(float[][] matrix, int firstRow, int secondRow) {
        float[] temp = matrix[firstRow];
        matrix[firstRow] = matrix[secondRow];
        matrix[secondRow] = temp;
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
        if (!(object instanceof Matrix4f)) {
            return false;
        }
        Matrix4f other = (Matrix4f) object;
        return Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(values));
    }

    @Override
    public String toString() {
        return "Matrix4f{" +
                "values=" + Arrays.toString(values) +
                '}';
    }
}
