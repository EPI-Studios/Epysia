package fr.epistudio.epysia.assets.procedural;

import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

final class ProceduralDocument {

    private ProceduralDocument() {
    }

    static Map<String, Object> read(AssetLocator locator, AssetLoadRequest request) {
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

    static int intOf(Map<String, Object> document, String key, int fallback) {
        return Math.round(floatOf(document, key, fallback));
    }

    static float floatOf(Map<String, Object> document, String key, float fallback) {
        Object value = document.get(key);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    static boolean booleanOf(Map<String, Object> document, String key) {
        return document.get(key) instanceof Boolean flag && flag;
    }

    static String stringOf(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value instanceof String text ? text : fallback;
    }
}
