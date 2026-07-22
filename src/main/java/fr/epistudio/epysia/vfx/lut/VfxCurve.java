package fr.epistudio.epysia.vfx.lut;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VfxCurve {

    public record Keyframe(float time, float value, float inTangent, float outTangent) {
    }

    public static final String ENCODING_PREFIX = "curve1:";

    private static final String SECTION_SEPARATOR = ":";
    private static final String KEYFRAME_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";
    private static final Comparator<Keyframe> BY_TIME = Comparator.comparingDouble(Keyframe::time);

    private final List<Keyframe> keyframes = new ArrayList<>();
    private float minimumBound;
    private float maximumBound = 1.0f;

    public static VfxCurve constant(float value) {
        VfxCurve curve = new VfxCurve();
        curve.addKeyframe(new Keyframe(0.0f, value, 0.0f, 0.0f));
        curve.addKeyframe(new Keyframe(1.0f, value, 0.0f, 0.0f));
        return curve;
    }

    public static VfxCurve linear(float startValue, float endValue) {
        VfxCurve curve = new VfxCurve();
        float slope = endValue - startValue;
        curve.addKeyframe(new Keyframe(0.0f, startValue, slope, slope));
        curve.addKeyframe(new Keyframe(1.0f, endValue, slope, slope));
        return curve;
    }

    public List<Keyframe> keyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public float minimumBound() {
        return minimumBound;
    }

    public float maximumBound() {
        return maximumBound;
    }

    public void setBounds(float minimum, float maximum) {
        this.minimumBound = Math.min(minimum, maximum);
        this.maximumBound = Math.max(minimum, maximum);
    }

    public int addKeyframe(Keyframe keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(BY_TIME);
        return keyframes.indexOf(keyframe);
    }

    public int setKeyframe(int index, Keyframe keyframe) {
        if (index < 0 || index >= keyframes.size()) {
            return index;
        }
        keyframes.set(index, keyframe);
        keyframes.sort(BY_TIME);
        return keyframes.indexOf(keyframe);
    }

    public void removeKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.remove(index);
        }
    }

    public void clearKeyframes() {
        keyframes.clear();
    }

    public float evaluate(float time) {
        if (keyframes.isEmpty()) {
            return 0.0f;
        }
        Keyframe first = keyframes.get(0);
        if (time <= first.time()) {
            return first.value();
        }
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.time()) {
            return last.value();
        }
        int index = segmentIndex(time);
        return hermite(keyframes.get(index), keyframes.get(index + 1), time);
    }

    private int segmentIndex(float time) {
        for (int index = keyframes.size() - 2; index > 0; index--) {
            if (keyframes.get(index).time() <= time) {
                return index;
            }
        }
        return 0;
    }

    private static float hermite(Keyframe start, Keyframe end, float time) {
        float span = end.time() - start.time();
        if (span <= 0.0f) {
            return end.value();
        }
        float progress = (time - start.time()) / span;
        float squared = progress * progress;
        float cubed = squared * progress;
        return (2.0f * cubed - 3.0f * squared + 1.0f) * start.value()
                + (cubed - 2.0f * squared + progress) * span * start.outTangent()
                + (-2.0f * cubed + 3.0f * squared) * end.value()
                + (cubed - squared) * span * end.inTangent();
    }

    public float[] sample(int count) {
        float[] samples = new float[Math.max(count, 0)];
        if (samples.length == 0) {
            return samples;
        }
        float divisor = samples.length > 1 ? samples.length - 1 : 1;
        for (int index = 0; index < samples.length; index++) {
            samples[index] = evaluate(index / divisor);
        }
        return samples;
    }

    public String encode() {
        StringBuilder builder = new StringBuilder(ENCODING_PREFIX);
        builder.append(minimumBound).append(FIELD_SEPARATOR).append(maximumBound).append(SECTION_SEPARATOR);
        for (int index = 0; index < keyframes.size(); index++) {
            if (index > 0) {
                builder.append(KEYFRAME_SEPARATOR);
            }
            appendKeyframe(builder, keyframes.get(index));
        }
        return builder.toString();
    }

    private static void appendKeyframe(StringBuilder builder, Keyframe keyframe) {
        builder.append(keyframe.time()).append(FIELD_SEPARATOR)
                .append(keyframe.value()).append(FIELD_SEPARATOR)
                .append(keyframe.inTangent()).append(FIELD_SEPARATOR)
                .append(keyframe.outTangent());
    }

    public static boolean isEncodedCurve(String text) {
        return text.startsWith(ENCODING_PREFIX);
    }

    public static VfxCurve decode(String text) {
        VfxCurve curve = new VfxCurve();
        if (!isEncodedCurve(text)) {
            return curve;
        }
        String[] sections = text.substring(ENCODING_PREFIX.length()).split(SECTION_SEPARATOR, -1);
        readBounds(curve, sections[0]);
        if (sections.length > 1) {
            readKeyframes(curve, sections[1]);
        }
        return curve;
    }

    private static void readBounds(VfxCurve curve, String section) {
        String[] fields = section.split(FIELD_SEPARATOR, -1);
        if (fields.length == 2) {
            curve.setBounds(parseFloat(fields[0]), parseFloat(fields[1]));
        }
    }

    private static void readKeyframes(VfxCurve curve, String section) {
        if (section.isEmpty()) {
            return;
        }
        for (String entry : section.split(KEYFRAME_SEPARATOR, -1)) {
            String[] fields = entry.split(FIELD_SEPARATOR, -1);
            if (fields.length == 4) {
                curve.addKeyframe(new Keyframe(parseFloat(fields[0]), parseFloat(fields[1]),
                        parseFloat(fields[2]), parseFloat(fields[3])));
            }
        }
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException invalid) {
            return 0.0f;
        }
    }

    @Override
    public String toString() {
        return encode();
    }
}
