package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.procedural.CurveTextureLoader;
import fr.epistudio.epysia.assets.procedural.GradientTextureLoader;
import fr.epistudio.epysia.assets.procedural.NoiseTextureLoader;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import fr.epistudio.epysia.vfx.lut.VfxGradient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProceduralDocumentModel {

    public enum Kind {
        NOISE,
        GRADIENT,
        CURVE
    }

    private static final List<String> NOISE_KINDS = List.of("VALUE", "FRACTAL", "CELLULAR");
    private static final int DEFAULT_NOISE_SIZE = 256;
    private static final int DEFAULT_OCTAVES = 4;
    private static final int DEFAULT_PERIOD = 8;
    private static final float DEFAULT_LACUNARITY = 2.0f;
    private static final float DEFAULT_GAIN = 0.5f;

    private final Kind kind;
    private final Path path;
    private int size = DEFAULT_NOISE_SIZE;
    private int seed;
    private int octaves = DEFAULT_OCTAVES;
    private int period = DEFAULT_PERIOD;
    private float lacunarity = DEFAULT_LACUNARITY;
    private float gain = DEFAULT_GAIN;
    private boolean inverted;
    private int noiseKindIndex = NOISE_KINDS.indexOf("FRACTAL");
    private VfxGradient gradient = VfxGradient.opaqueWhite();
    private VfxCurve curve = VfxCurve.linear(0.0f, 1.0f);

    private ProceduralDocumentModel(Kind kind, Path path) {
        this.kind = kind;
        this.path = path;
    }

    public static ProceduralDocumentModel empty() {
        return new ProceduralDocumentModel(Kind.NOISE, Path.of(""));
    }

    public static ProceduralDocumentModel read(Path file) {
        ProceduralDocumentModel model = new ProceduralDocumentModel(kindOf(file), file);
        model.adopt(documentOf(file));
        return model;
    }

    private static Kind kindOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(GradientTextureLoader.EXTENSION)) {
            return Kind.GRADIENT;
        }
        return name.endsWith(CurveTextureLoader.EXTENSION) ? Kind.CURVE : Kind.NOISE;
    }

    private static Map<String, Object> documentOf(Path file) {
        try {
            return new JsonReader(Files.readString(file)).readRootObject();
        } catch (IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }

    private void adopt(Map<String, Object> document) {
        size = intOf(document, kind == Kind.NOISE ? "size" : "width", defaultSize());
        seed = intOf(document, "seed", 0);
        octaves = intOf(document, "octaves", DEFAULT_OCTAVES);
        period = intOf(document, "period", DEFAULT_PERIOD);
        lacunarity = floatOf(document, "lacunarity", DEFAULT_LACUNARITY);
        gain = floatOf(document, "gain", DEFAULT_GAIN);
        inverted = booleanOf(document, kind == Kind.GRADIENT ? "vertical" : "inverted");
        noiseKindIndex = Math.max(0, NOISE_KINDS.indexOf(stringOf(document, "kind", "FRACTAL").toUpperCase(Locale.ROOT)));
        gradient = VfxGradient.decode(stringOf(document, GradientTextureLoader.GRADIENT_KEY, ""));
        curve = VfxCurve.decode(stringOf(document, CurveTextureLoader.CURVE_KEY, ""));
    }

    private int defaultSize() {
        return kind == Kind.NOISE ? DEFAULT_NOISE_SIZE : GradientTextureLoader.DEFAULT_WIDTH;
    }

    public boolean matches(Path other) {
        return path.equals(other);
    }

    public Kind kind() {
        return kind;
    }

    public int size() {
        return size;
    }

    public void setSize(int value) {
        size = Math.clamp(value, 2, 2048);
    }

    public int seed() {
        return seed;
    }

    public void setSeed(int value) {
        seed = value;
    }

    public int octaves() {
        return octaves;
    }

    public void setOctaves(int value) {
        octaves = Math.clamp(value, 1, 8);
    }

    public int period() {
        return period;
    }

    public void setPeriod(int value) {
        period = Math.clamp(value, 1, 64);
    }

    public float lacunarity() {
        return lacunarity;
    }

    public void setLacunarity(float value) {
        lacunarity = Math.clamp(value, 1.0f, 8.0f);
    }

    public float gain() {
        return gain;
    }

    public void setGain(float value) {
        gain = Math.clamp(value, 0.05f, 1.0f);
    }

    public boolean inverted() {
        return inverted;
    }

    public void setInverted(boolean value) {
        inverted = value;
    }

    public int noiseKindIndex() {
        return noiseKindIndex;
    }

    public void setNoiseKindIndex(int value) {
        noiseKindIndex = Math.clamp(value, 0, NOISE_KINDS.size() - 1);
    }

    public String noiseKindName() {
        return NOISE_KINDS.get(noiseKindIndex);
    }

    public VfxGradient gradient() {
        return gradient;
    }

    public VfxCurve curve() {
        return curve;
    }

    public String toJson() {
        return switch (kind) {
            case NOISE -> """
                    {
                      "kind": "%s",
                      "size": %d,
                      "seed": %d,
                      "octaves": %d,
                      "lacunarity": %s,
                      "gain": %s,
                      "period": %d,
                      "inverted": %b
                    }
                    """.formatted(noiseKindName().toLowerCase(Locale.ROOT), size, seed, octaves,
                    format(lacunarity), format(gain), period, inverted);
            case GRADIENT -> """
                    {
                      "width": %d,
                      "vertical": %b,
                      "%s": "%s"
                    }
                    """.formatted(size, inverted, GradientTextureLoader.GRADIENT_KEY, gradient.encode());
            case CURVE -> """
                    {
                      "width": %d,
                      "%s": "%s"
                    }
                    """.formatted(size, CurveTextureLoader.CURVE_KEY, curve.encode());
        };
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public static String defaultDocument(String extension) {
        if (extension.equals(GradientTextureLoader.EXTENSION)) {
            return new ProceduralDocumentModel(Kind.GRADIENT, Path.of("")).toJson();
        }
        if (extension.equals(CurveTextureLoader.EXTENSION)) {
            return new ProceduralDocumentModel(Kind.CURVE, Path.of("")).toJson();
        }
        return new ProceduralDocumentModel(Kind.NOISE, Path.of("")).toJson();
    }

    public static String noiseExtension() {
        return NoiseTextureLoader.EXTENSION;
    }

    private static int intOf(Map<String, Object> document, String key, int fallback) {
        return Math.round(floatOf(document, key, fallback));
    }

    private static float floatOf(Map<String, Object> document, String key, float fallback) {
        return document.get(key) instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean booleanOf(Map<String, Object> document, String key) {
        return document.get(key) instanceof Boolean flag && flag;
    }

    private static String stringOf(Map<String, Object> document, String key, String fallback) {
        return document.get(key) instanceof String text ? text : fallback;
    }
}
