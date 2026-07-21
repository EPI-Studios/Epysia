package fr.epistudio.epysia.animation;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinningPaletteTest {

    private static ByteBuffer allocate(int jointCount) {
        return ByteBuffer.allocate(jointCount * SkinningPalette.BYTES_PER_JOINT).order(ByteOrder.nativeOrder());
    }

    @Test
    void identityPacksToIdentityRows() {
        Matrix4f[] matrices = {new Matrix4f(), new Matrix4f()};
        ByteBuffer target = allocate(matrices.length);

        SkinningPalette.pack(matrices, target);

        float[] expected = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        for (int joint = 0; joint < matrices.length; joint++) {
            for (int component = 0; component < expected.length; component++) {
                int floatIndex = joint * 12 + component;
                assertEquals(expected[component], target.getFloat(floatIndex * 4), 0.0f);
            }
        }
    }

    @Test
    void translationSurvivesPacking() {
        Matrix4f[] matrices = {new Matrix4f().translation(0.0f, 2.0f, 0.0f)};
        ByteBuffer target = allocate(matrices.length);

        SkinningPalette.pack(matrices, target);

        int rowOneWordFloatIndex = 1 * 4 + 3;
        assertEquals(2.0f, target.getFloat(rowOneWordFloatIndex * 4), 0.0f);
    }
}
