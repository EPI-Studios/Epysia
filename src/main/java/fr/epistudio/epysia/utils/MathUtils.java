package fr.epistudio.epysia.utils;

final class MathUtils {

    static final float EPSILON = 1.0e-6f;

    private MathUtils() {
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static boolean nearlyZero(float value) {
        return Math.abs(value) <= EPSILON;
    }

    static boolean nearlyEquals(float left, float right) {
        return Math.abs(left - right) <= EPSILON;
    }
}
