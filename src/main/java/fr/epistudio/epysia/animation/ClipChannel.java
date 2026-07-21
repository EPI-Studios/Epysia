package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;

public record ClipChannel(int jointIndex, ClipProperty property, ClipInterpolation interpolation,
                           float[] times, float[] values) {

    public ClipChannel {
        validateValueCount(property, interpolation, times, values);
        validateStrictlyIncreasingTimes(times);
    }

    private static void validateValueCount(ClipProperty property, ClipInterpolation interpolation,
                                            float[] times, float[] values) {
        int multiplier = interpolation == ClipInterpolation.CUBIC_SPLINE ? 3 : 1;
        int expected = times.length * property.componentCount() * multiplier;
        if (values.length != expected) {
            throw new EpysiaException("ClipChannel expected " + expected + " values but got " + values.length + ".");
        }
    }

    private static void validateStrictlyIncreasingTimes(float[] times) {
        for (int index = 1; index < times.length; index++) {
            if (times[index] <= times[index - 1]) {
                throw new EpysiaException("ClipChannel times must be strictly increasing at index " + index + ".");
            }
        }
    }
}
