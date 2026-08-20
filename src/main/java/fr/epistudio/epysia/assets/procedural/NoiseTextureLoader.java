package fr.epistudio.epysia.assets.procedural;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public final class NoiseTextureLoader implements AssetLoader<TextureHandle> {

    public static final String EXTENSION = ".epynoise";

    private static final int DEFAULT_SIZE = 256;
    private static final int MAXIMUM_SIZE = 2048;
    private static final int DEFAULT_OCTAVES = 4;
    private static final float DEFAULT_LACUNARITY = 2.0f;
    private static final float DEFAULT_GAIN = 0.5f;
    private static final int DEFAULT_PERIOD = 8;

    @Override
    public Class<TextureHandle> assetType() {
        return TextureHandle.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public TextureHandle load(EngineServices services, AssetLoadRequest request) {
        Map<String, Object> document = read(services.assets().locator(), request);
        int size = Math.clamp(intOf(document, "size", DEFAULT_SIZE), 1, MAXIMUM_SIZE);
        NoiseKind kind = NoiseKind.of(stringOf(document, "kind", NoiseKind.FRACTAL.name()));
        int seed = intOf(document, "seed", 0);
        int octaves = intOf(document, "octaves", DEFAULT_OCTAVES);
        float lacunarity = floatOf(document, "lacunarity", DEFAULT_LACUNARITY);
        float gain = floatOf(document, "gain", DEFAULT_GAIN);
        int period = Math.max(1, intOf(document, "period", DEFAULT_PERIOD));
        boolean inverted = booleanOf(document, "inverted");
        ByteBuffer surface = GeneratedTexture.surface(size, size);
        paint(surface, size, kind, seed, octaves, lacunarity, gain, period, inverted);
        return GeneratedTexture.upload(services.renderBackend(), size, size, surface,
                TextureWrap.REPEAT, SamplerFilter.LINEAR);
    }

    private static void paint(ByteBuffer surface, int size, NoiseKind kind, int seed, int octaves,
                              float lacunarity, float gain, int period, boolean inverted) {
        float step = (float) period / size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float sample = sampleOf(kind, seed, x * step, y * step, octaves, lacunarity, gain, period);
                float level = inverted ? 1.0f - sample : sample;
                GeneratedTexture.write(surface, y * size + x, level, level, level, 1.0f);
            }
        }
    }

    private static float sampleOf(NoiseKind kind, int seed, float x, float y, int octaves,
                                  float lacunarity, float gain, int period) {
        return switch (kind) {
            case VALUE -> ProceduralNoise.value(seed, x, y, period);
            case CELLULAR -> ProceduralNoise.cellular(seed, x, y, period);
            case FRACTAL -> ProceduralNoise.fractal(seed, x, y, octaves, lacunarity, gain, period);
        };
    }

    private static Map<String, Object> read(AssetLocator locator, AssetLoadRequest request) {
        Optional<AssetSource> source = request.source(locator);
        if (source.isEmpty()) {
            return Map.of();
        }
        try (InputStream stream = source.get().open().orElse(null)) {
            if (stream == null) {
                return Map.of();
            }
            return new JsonReader(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).readRootObject();
        } catch (IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }

    private static int intOf(Map<String, Object> document, String key, int fallback) {
        return Math.round(floatOf(document, key, fallback));
    }

    private static float floatOf(Map<String, Object> document, String key, float fallback) {
        Object value = document.get(key);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean booleanOf(Map<String, Object> document, String key) {
        return document.get(key) instanceof Boolean flag && flag;
    }

    private static String stringOf(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value instanceof String text ? text : fallback;
    }

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }

    private enum NoiseKind {
        VALUE,
        FRACTAL,
        CELLULAR;

        static NoiseKind of(String name) {
            for (NoiseKind candidate : values()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            return FRACTAL;
        }
    }
}
