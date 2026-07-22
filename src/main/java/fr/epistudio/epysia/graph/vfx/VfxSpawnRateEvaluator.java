package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.vfx.lut.VfxCurve;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class VfxSpawnRateEvaluator {

    static final int SAMPLE_COUNT = 32;

    private final GraphAsset asset;
    private final Set<Integer> visiting = new HashSet<>();

    VfxSpawnRateEvaluator(GraphAsset asset) {
        this.asset = asset;
    }

    float[] samples(GraphNode output, String pin, float fallback) {
        float[] samples = new float[SAMPLE_COUNT];
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            samples[index] = inputOf(output, pin, fallback, index / (float) (SAMPLE_COUNT - 1));
        }
        return samples;
    }

    static float mean(float[] samples) {
        float total = 0.0f;
        for (float sample : samples) {
            total += sample;
        }
        return samples.length == 0 ? 0.0f : total / samples.length;
    }

    private float inputOf(GraphNode target, String pin, float fallback, float time) {
        Optional<GraphEdge> edge = asset.edgeInto(target.id(), pin);
        if (edge.isEmpty()) {
            return GraphValues.asFloat(target.values().getOrDefault(pin, fallback));
        }
        GraphNode source = asset.findNode(edge.get().fromNode()).orElseThrow(() ->
                new EpysiaException("VFX graph edge references a missing node."));
        return evaluate(source, time);
    }

    private float evaluate(GraphNode source, float time) {
        if (!visiting.add(source.id())) {
            throw new EpysiaException("VFX graph contains a cycle through node " + source.id() + ".");
        }
        try {
            return evaluateByType(source, time);
        } finally {
            visiting.remove(source.id());
        }
    }

    private float evaluateByType(GraphNode source, float time) {
        return switch (source.typeKey()) {
            case VfxNodes.EFFECT_TIME_NORMALIZED -> time;
            case VfxNodes.CONSTANT -> setting(source, VfxNodes.VALUE_X_SETTING, 0.0f);
            case VfxNodes.CURVE -> evaluateCurve(source, time);
            case VfxNodes.RANDOM_RANGE -> (setting(source, VfxNodes.MINIMUM_SETTING, 0.0f)
                    + setting(source, VfxNodes.MAXIMUM_SETTING, 1.0f)) * 0.5f;
            default -> evaluateMath(source, time);
        };
    }

    private float evaluateMath(GraphNode source, float time) {
        return switch (source.typeKey()) {
            case VfxNodes.MATH_ADD -> left(source, time, 0.0f) + right(source, time, 0.0f);
            case VfxNodes.MATH_SUBTRACT -> left(source, time, 0.0f) - right(source, time, 0.0f);
            case VfxNodes.MATH_MULTIPLY -> left(source, time, 1.0f) * right(source, time, 1.0f);
            case VfxNodes.MATH_DIVIDE -> divide(left(source, time, 1.0f), right(source, time, 1.0f));
            case VfxNodes.MATH_DOT -> left(source, time, 0.0f) * right(source, time, 0.0f);
            case VfxNodes.MATH_LERP -> lerp(source, time);
            case VfxNodes.MATH_CLAMP -> clamp(source, time);
            case VfxNodes.MATH_REMAP -> remap(source, time);
            case VfxNodes.MATH_ONE_MINUS -> 1.0f - value(source, time);
            case VfxNodes.MATH_LENGTH -> Math.abs(value(source, time));
            case VfxNodes.MATH_NORMALIZE -> Math.signum(value(source, time));
            case VfxNodes.MATH_SINE -> (float) Math.sin(value(source, time));
            default -> throw new EpysiaException(
                    "Spawn Rate cannot evaluate node " + source.typeKey() + " on the processor.");
        };
    }

    private float evaluateCurve(GraphNode source, float time) {
        float progress = inputOf(source, VfxNodes.TIME_PIN, time, time);
        VfxCurve curve = curveOf(source);
        float minimum = setting(source, VfxNodes.MINIMUM_SETTING, 0.0f);
        float maximum = setting(source, VfxNodes.MAXIMUM_SETTING, 1.0f);
        return minimum + (maximum - minimum) * curve.evaluate(progress);
    }

    private static VfxCurve curveOf(GraphNode source) {
        Object encoded = source.values().get(VfxNodes.CURVE_SETTING);
        if (encoded == null) {
            return VfxCurve.linear(0.0f, 1.0f);
        }
        return VfxCurve.decode(GraphValues.asString(encoded));
    }

    private float lerp(GraphNode source, float time) {
        float start = left(source, time, 0.0f);
        float end = right(source, time, 0.0f);
        return start + (end - start) * inputOf(source, VfxNodes.T_PIN, 0.5f, time);
    }

    private float clamp(GraphNode source, float time) {
        float minimum = inputOf(source, VfxNodes.MINIMUM_PIN, 0.0f, time);
        float maximum = inputOf(source, VfxNodes.MAXIMUM_PIN, 1.0f, time);
        return Math.min(Math.max(value(source, time), minimum), maximum);
    }

    private float remap(GraphNode source, float time) {
        float fromMinimum = inputOf(source, VfxNodes.FROM_MINIMUM_PIN, 0.0f, time);
        float fromMaximum = inputOf(source, VfxNodes.FROM_MAXIMUM_PIN, 1.0f, time);
        float toMinimum = inputOf(source, VfxNodes.TO_MINIMUM_PIN, 0.0f, time);
        float toMaximum = inputOf(source, VfxNodes.TO_MAXIMUM_PIN, 1.0f, time);
        return toMinimum + (value(source, time) - fromMinimum)
                * divide(toMaximum - toMinimum, fromMaximum - fromMinimum);
    }

    private static float divide(float numerator, float denominator) {
        if (Math.abs(denominator) < 1.0e-5f) {
            return numerator / (denominator < 0.0f ? -1.0e-5f : 1.0e-5f);
        }
        return numerator / denominator;
    }

    private float value(GraphNode source, float time) {
        return inputOf(source, VfxNodes.VALUE_PIN, 0.0f, time);
    }

    private float left(GraphNode source, float time, float fallback) {
        return inputOf(source, VfxNodes.A_PIN, fallback, time);
    }

    private float right(GraphNode source, float time, float fallback) {
        return inputOf(source, VfxNodes.B_PIN, fallback, time);
    }

    private static float setting(GraphNode node, String key, float fallback) {
        return GraphValues.asFloat(node.values().getOrDefault(key, fallback));
    }
}
