package fr.epistudio.epysia.vfx.lut;

import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VfxGradient {

    public record ColorStop(float time, float red, float green, float blue) {
    }

    public record AlphaStop(float time, float alpha) {
    }

    public static final String ENCODING_PREFIX = "gradient1:";

    private static final String SECTION_SEPARATOR = ":";
    private static final String STOP_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";
    private static final Comparator<ColorStop> COLOR_BY_TIME = Comparator.comparingDouble(ColorStop::time);
    private static final Comparator<AlphaStop> ALPHA_BY_TIME = Comparator.comparingDouble(AlphaStop::time);

    private final List<ColorStop> colorStops = new ArrayList<>();
    private final List<AlphaStop> alphaStops = new ArrayList<>();

    public static VfxGradient opaqueWhite() {
        VfxGradient gradient = new VfxGradient();
        gradient.addColorStop(new ColorStop(0.0f, 1.0f, 1.0f, 1.0f));
        gradient.addColorStop(new ColorStop(1.0f, 1.0f, 1.0f, 1.0f));
        gradient.addAlphaStop(new AlphaStop(0.0f, 1.0f));
        gradient.addAlphaStop(new AlphaStop(1.0f, 1.0f));
        return gradient;
    }

    public List<ColorStop> colorStops() {
        return Collections.unmodifiableList(colorStops);
    }

    public List<AlphaStop> alphaStops() {
        return Collections.unmodifiableList(alphaStops);
    }

    public int addColorStop(ColorStop stop) {
        colorStops.add(stop);
        colorStops.sort(COLOR_BY_TIME);
        return colorStops.indexOf(stop);
    }

    public int addAlphaStop(AlphaStop stop) {
        alphaStops.add(stop);
        alphaStops.sort(ALPHA_BY_TIME);
        return alphaStops.indexOf(stop);
    }

    public int setColorStop(int index, ColorStop stop) {
        if (index < 0 || index >= colorStops.size()) {
            return index;
        }
        colorStops.set(index, stop);
        colorStops.sort(COLOR_BY_TIME);
        return colorStops.indexOf(stop);
    }

    public int setAlphaStop(int index, AlphaStop stop) {
        if (index < 0 || index >= alphaStops.size()) {
            return index;
        }
        alphaStops.set(index, stop);
        alphaStops.sort(ALPHA_BY_TIME);
        return alphaStops.indexOf(stop);
    }

    public void removeColorStop(int index) {
        if (index >= 0 && index < colorStops.size()) {
            colorStops.remove(index);
        }
    }

    public void removeAlphaStop(int index) {
        if (index >= 0 && index < alphaStops.size()) {
            alphaStops.remove(index);
        }
    }

    public void clearStops() {
        colorStops.clear();
        alphaStops.clear();
    }

    public Vector4f evaluate(float time) {
        Vector4f color = evaluateColor(time);
        color.w = evaluateAlpha(time);
        return color;
    }

    private Vector4f evaluateColor(float time) {
        if (colorStops.isEmpty()) {
            return new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
        ColorStop first = colorStops.get(0);
        ColorStop last = colorStops.get(colorStops.size() - 1);
        if (time <= first.time()) {
            return new Vector4f(first.red(), first.green(), first.blue(), 1.0f);
        }
        if (time >= last.time()) {
            return new Vector4f(last.red(), last.green(), last.blue(), 1.0f);
        }
        int index = colorSegmentIndex(time);
        return blendColor(colorStops.get(index), colorStops.get(index + 1), time);
    }

    private int colorSegmentIndex(float time) {
        for (int index = colorStops.size() - 2; index > 0; index--) {
            if (colorStops.get(index).time() <= time) {
                return index;
            }
        }
        return 0;
    }

    private static Vector4f blendColor(ColorStop start, ColorStop end, float time) {
        float progress = progressBetween(start.time(), end.time(), time);
        return new Vector4f(
                mix(start.red(), end.red(), progress),
                mix(start.green(), end.green(), progress),
                mix(start.blue(), end.blue(), progress),
                1.0f);
    }

    private float evaluateAlpha(float time) {
        if (alphaStops.isEmpty()) {
            return 1.0f;
        }
        AlphaStop first = alphaStops.get(0);
        AlphaStop last = alphaStops.get(alphaStops.size() - 1);
        if (time <= first.time()) {
            return first.alpha();
        }
        if (time >= last.time()) {
            return last.alpha();
        }
        int index = alphaSegmentIndex(time);
        AlphaStop start = alphaStops.get(index);
        AlphaStop end = alphaStops.get(index + 1);
        return mix(start.alpha(), end.alpha(), progressBetween(start.time(), end.time(), time));
    }

    private int alphaSegmentIndex(float time) {
        for (int index = alphaStops.size() - 2; index > 0; index--) {
            if (alphaStops.get(index).time() <= time) {
                return index;
            }
        }
        return 0;
    }

    private static float progressBetween(float start, float end, float time) {
        float span = end - start;
        return span <= 0.0f ? 1.0f : (time - start) / span;
    }

    private static float mix(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public float[] sample(int count) {
        int clamped = Math.max(count, 0);
        float[] samples = new float[clamped * 4];
        if (clamped == 0) {
            return samples;
        }
        float divisor = clamped > 1 ? clamped - 1 : 1;
        for (int index = 0; index < clamped; index++) {
            Vector4f color = evaluate(index / divisor);
            samples[index * 4] = color.x;
            samples[index * 4 + 1] = color.y;
            samples[index * 4 + 2] = color.z;
            samples[index * 4 + 3] = color.w;
        }
        return samples;
    }

    public String encode() {
        StringBuilder builder = new StringBuilder(ENCODING_PREFIX);
        for (int index = 0; index < colorStops.size(); index++) {
            ColorStop stop = colorStops.get(index);
            appendSeparator(builder, index);
            builder.append(stop.time()).append(FIELD_SEPARATOR).append(stop.red())
                    .append(FIELD_SEPARATOR).append(stop.green())
                    .append(FIELD_SEPARATOR).append(stop.blue());
        }
        builder.append(SECTION_SEPARATOR);
        for (int index = 0; index < alphaStops.size(); index++) {
            AlphaStop stop = alphaStops.get(index);
            appendSeparator(builder, index);
            builder.append(stop.time()).append(FIELD_SEPARATOR).append(stop.alpha());
        }
        return builder.toString();
    }

    private static void appendSeparator(StringBuilder builder, int index) {
        if (index > 0) {
            builder.append(STOP_SEPARATOR);
        }
    }

    public static boolean isEncodedGradient(String text) {
        return text.startsWith(ENCODING_PREFIX);
    }

    public static VfxGradient decode(String text) {
        VfxGradient gradient = new VfxGradient();
        if (!isEncodedGradient(text)) {
            return gradient;
        }
        String[] sections = text.substring(ENCODING_PREFIX.length()).split(SECTION_SEPARATOR, -1);
        readColorStops(gradient, sections[0]);
        if (sections.length > 1) {
            readAlphaStops(gradient, sections[1]);
        }
        return gradient;
    }

    private static void readColorStops(VfxGradient gradient, String section) {
        if (section.isEmpty()) {
            return;
        }
        for (String entry : section.split(STOP_SEPARATOR, -1)) {
            String[] fields = entry.split(FIELD_SEPARATOR, -1);
            if (fields.length == 4) {
                gradient.addColorStop(new ColorStop(parseFloat(fields[0]), parseFloat(fields[1]),
                        parseFloat(fields[2]), parseFloat(fields[3])));
            }
        }
    }

    private static void readAlphaStops(VfxGradient gradient, String section) {
        if (section.isEmpty()) {
            return;
        }
        for (String entry : section.split(STOP_SEPARATOR, -1)) {
            String[] fields = entry.split(FIELD_SEPARATOR, -1);
            if (fields.length == 2) {
                gradient.addAlphaStop(new AlphaStop(parseFloat(fields[0]), parseFloat(fields[1])));
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
