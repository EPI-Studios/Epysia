package fr.epistudio.epysia.render.sprite;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Object2dUniformLayoutTest {
    private static Vector2f shaderTransform(float[] uniform, float x, float y) {
        return new Vector2f(uniform[0] * x + uniform[2] * y + uniform[4],
                uniform[1] * x + uniform[3] * y + uniform[5]);
    }

    private static float[] packed(Matrix3x2f matrix) {
        return new float[] {matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(),
                matrix.m20(), matrix.m21()};
    }

    @Test
    void packedUniformMatchesCpuTransformUnderRotationAndScale() {
        Matrix3x2f matrix = new Matrix3x2f()
                .translate(12.5f, -3.25f)
                .rotate(0.7f)
                .scale(2.0f, 0.5f);
        float[] uniform = packed(matrix);
        for (float[] point : new float[][] {{0.0f, 0.0f}, {1.0f, 0.0f}, {0.0f, 1.0f}, {-7.5f, 4.25f}}) {
            Vector2f expected = matrix.transformPosition(new Vector2f(point[0], point[1]));
            Vector2f actual = shaderTransform(uniform, point[0], point[1]);
            assertEquals(expected.x, actual.x, 1.0e-5f);
            assertEquals(expected.y, actual.y, 1.0e-5f);
        }
    }
}
