package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Vector3f;

public final class SphericalHarmonics {

    public static final int COEFFICIENT_COUNT = 9;
    public static final int FLOAT_COUNT = COEFFICIENT_COUNT * 3;

    private static final float BASIS_0 = 0.282095f;
    private static final float BASIS_1 = 0.488603f;
    private static final float BASIS_2 = 1.092548f;
    private static final float BASIS_3 = 0.315392f;
    private static final float BASIS_4 = 0.546274f;

    private static final float CONVOLUTION_1 = 0.429043f;
    private static final float CONVOLUTION_2 = 0.511664f;
    private static final float CONVOLUTION_3 = 0.743125f;
    private static final float CONVOLUTION_4 = 0.886227f;
    private static final float CONVOLUTION_5 = 0.247708f;

    private static final float INVERSE_PI = (float) (1.0 / Math.PI);

    private SphericalHarmonics() {
    }

    public static float[] project(float[][] faceRadiance, int faceSize) {
        requireFaces(faceRadiance, faceSize);
        float[] coefficients = new float[FLOAT_COUNT];
        float[] basis = new float[COEFFICIENT_COUNT];
        Vector3f direction = new Vector3f();
        for (int face = 0; face < CubeCaptureFace.COUNT; face++) {
            projectFace(faceRadiance[face], face, faceSize, coefficients, basis, direction);
        }
        return coefficients;
    }

    private static void projectFace(float[] radiance, int face, int faceSize,
                                    float[] coefficients, float[] basis, Vector3f direction) {
        CubeCaptureFace orientation = CubeCaptureFace.at(face);
        for (int y = 0; y < faceSize; y++) {
            for (int x = 0; x < faceSize; x++) {
                orientation.direction(x, y, faceSize, direction);
                float weight = CubeCaptureFace.solidAngle(x, y, faceSize);
                evaluateBasis(direction, basis);
                accumulateTexel(radiance, (y * faceSize + x) * 3, coefficients, basis, weight);
            }
        }
    }

    private static void accumulateTexel(float[] radiance, int texelOffset,
                                        float[] coefficients, float[] basis, float weight) {
        for (int coefficient = 0; coefficient < COEFFICIENT_COUNT; coefficient++) {
            float factor = basis[coefficient] * weight;
            int base = coefficient * 3;
            coefficients[base] += radiance[texelOffset] * factor;
            coefficients[base + 1] += radiance[texelOffset + 1] * factor;
            coefficients[base + 2] += radiance[texelOffset + 2] * factor;
        }
    }

    private static void evaluateBasis(Vector3f direction, float[] basis) {
        float x = direction.x;
        float y = direction.y;
        float z = direction.z;
        basis[0] = BASIS_0;
        basis[1] = BASIS_1 * y;
        basis[2] = BASIS_1 * z;
        basis[3] = BASIS_1 * x;
        basis[4] = BASIS_2 * x * y;
        basis[5] = BASIS_2 * y * z;
        basis[6] = BASIS_3 * (3.0f * z * z - 1.0f);
        basis[7] = BASIS_2 * x * z;
        basis[8] = BASIS_4 * (x * x - y * y);
    }

    public static Vector3f evaluateIrradiance(float[] coefficients, Vector3f normal, Vector3f destination) {
        if (coefficients.length < FLOAT_COUNT) {
            throw new EpysiaException("Spherical harmonics evaluation needs " + FLOAT_COUNT
                    + " floats, got " + coefficients.length);
        }
        destination.zero();
        float x = normal.x;
        float y = normal.y;
        float z = normal.z;
        addCoefficient(destination, coefficients, 0, CONVOLUTION_4);
        addCoefficient(destination, coefficients, 1, 2.0f * CONVOLUTION_2 * y);
        addCoefficient(destination, coefficients, 2, 2.0f * CONVOLUTION_2 * z);
        addCoefficient(destination, coefficients, 3, 2.0f * CONVOLUTION_2 * x);
        addCoefficient(destination, coefficients, 4, 2.0f * CONVOLUTION_1 * x * y);
        addCoefficient(destination, coefficients, 5, 2.0f * CONVOLUTION_1 * y * z);
        addCoefficient(destination, coefficients, 6, CONVOLUTION_3 * z * z - CONVOLUTION_5);
        addCoefficient(destination, coefficients, 7, 2.0f * CONVOLUTION_1 * x * z);
        addCoefficient(destination, coefficients, 8, CONVOLUTION_1 * (x * x - y * y));
        return destination.mul(INVERSE_PI).max(new Vector3f(0.0f));
    }

    private static void addCoefficient(Vector3f destination, float[] coefficients, int coefficient, float factor) {
        int base = coefficient * 3;
        destination.x += coefficients[base] * factor;
        destination.y += coefficients[base + 1] * factor;
        destination.z += coefficients[base + 2] * factor;
    }

    private static void requireFaces(float[][] faceRadiance, int faceSize) {
        if (faceRadiance.length != CubeCaptureFace.COUNT) {
            throw new EpysiaException("Cubemap projection needs six faces, got " + faceRadiance.length);
        }
        int expected = faceSize * faceSize * 3;
        for (float[] face : faceRadiance) {
            if (face.length != expected) {
                throw new EpysiaException("Cubemap face needs " + expected + " floats, got " + face.length);
            }
        }
    }
}
