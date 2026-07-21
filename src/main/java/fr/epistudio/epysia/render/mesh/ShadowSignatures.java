package fr.epistudio.epysia.render.mesh;

import org.joml.Matrix4f;

final class ShadowSignatures {

    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final int LOW_WORD_SHIFT = 32;
    private static final long LOW_WORD_MASK = 0xFFFFFFFFL;

    private ShadowSignatures() {
    }

    static long seed() {
        return FNV_OFFSET_BASIS;
    }

    static long mix(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }

    static long mixMatrix(long hash, Matrix4f matrix) {
        long result = mix(hash, packPair(matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03()));
        result = mix(result, packPair(matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13()));
        result = mix(result, packPair(matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23()));
        return mix(result, packPair(matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()));
    }

    private static long packPair(float first, float second, float third, float fourth) {
        return pack(first, second) * FNV_PRIME ^ pack(third, fourth);
    }

    private static long pack(float high, float low) {
        return ((long) Float.floatToRawIntBits(high) << LOW_WORD_SHIFT)
                | (Float.floatToRawIntBits(low) & LOW_WORD_MASK);
    }
}
