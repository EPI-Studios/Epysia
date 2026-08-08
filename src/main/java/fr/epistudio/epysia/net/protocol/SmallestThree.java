package fr.epistudio.epysia.net.protocol;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class SmallestThree {
    private static final int COMPONENT_BITS = 10;
    private static final int COMPONENT_MASK = (1 << COMPONENT_BITS) - 1;
    private static final int COMPONENT_RANGE = COMPONENT_MASK;
    private static final float LARGEST_MAGNITUDE = 0.70710678f;
    private static final int COMPONENT_COUNT = 4;

    private SmallestThree() {
    }

    public static int pack(Quaternionfc rotation) {
        float[] components = {rotation.x(), rotation.y(), rotation.z(), rotation.w()};
        int largest = indexOfLargestMagnitude(components);
        float sign = components[largest] < 0.0f ? -1.0f : 1.0f;
        int packed = largest;
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            if (index != largest) {
                packed = (packed << COMPONENT_BITS) | quantise(components[index] * sign);
            }
        }
        return packed;
    }

    private static int indexOfLargestMagnitude(float[] components) {
        int largest = 0;
        for (int index = 1; index < COMPONENT_COUNT; index++) {
            if (Math.abs(components[index]) > Math.abs(components[largest])) {
                largest = index;
            }
        }
        return largest;
    }

    private static int quantise(float value) {
        float normalized = (Math.clamp(value, -LARGEST_MAGNITUDE, LARGEST_MAGNITUDE)
                / LARGEST_MAGNITUDE + 1.0f) * 0.5f;
        return Math.clamp(Math.round(normalized * COMPONENT_RANGE), 0, COMPONENT_MASK);
    }

    private static float dequantise(int stored) {
        return ((stored / (float) COMPONENT_RANGE) * 2.0f - 1.0f) * LARGEST_MAGNITUDE;
    }

    public static Quaternionf unpack(int packed) {
        float[] stored = new float[COMPONENT_COUNT - 1];
        int remaining = packed;
        for (int slot = stored.length - 1; slot >= 0; slot--) {
            stored[slot] = dequantise(remaining & COMPONENT_MASK);
            remaining >>>= COMPONENT_BITS;
        }
        int largest = remaining & 0x3;
        return rebuild(stored, largest);
    }

    private static Quaternionf rebuild(float[] stored, int largest) {
        float[] components = new float[COMPONENT_COUNT];
        float sumOfSquares = 0.0f;
        int cursor = 0;
        for (int index = 0; index < COMPONENT_COUNT; index++) {
            if (index == largest) {
                continue;
            }
            components[index] = stored[cursor];
            sumOfSquares += components[index] * components[index];
            cursor++;
        }
        components[largest] = (float) Math.sqrt(Math.max(0.0f, 1.0f - sumOfSquares));
        return new Quaternionf(components[0], components[1], components[2], components[3]).normalize();
    }
}
