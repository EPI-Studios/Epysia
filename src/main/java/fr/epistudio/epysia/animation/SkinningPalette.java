package fr.epistudio.epysia.animation;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

public final class SkinningPalette {

    public static final int ROWS_PER_JOINT = 3;
    public static final int BYTES_PER_JOINT = ROWS_PER_JOINT * 4 * Float.BYTES;

    private SkinningPalette() {
    }

    public static void pack(Matrix4f[] matrices, ByteBuffer target) {
        pack(matrices, target, new Vector4f());
    }

    public static void pack(Matrix4f[] matrices, ByteBuffer target, Vector4f rowScratch) {
        target.clear();
        for (Matrix4f matrix : matrices) {
            for (int rowIndex = 0; rowIndex < ROWS_PER_JOINT; rowIndex++) {
                matrix.getRow(rowIndex, rowScratch);
                target.putFloat(rowScratch.x).putFloat(rowScratch.y)
                        .putFloat(rowScratch.z).putFloat(rowScratch.w);
            }
        }
        target.flip();
    }
}
