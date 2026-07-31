package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.assets.AssetVariant;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record ImpostorImportSettings(String mode, int gridSize, int tileSize) {

    public static final String MODE_KEY = "impostor";
    public static final String GRID_SIZE_KEY = "impostorGrid";
    public static final String TILE_SIZE_KEY = "impostorTile";

    public static final String MODE_AUTOMATIC = "auto";
    public static final String MODE_ALWAYS = "on";
    public static final String MODE_NEVER = "off";

    public static final int DEFAULT_GRID_SIZE = 8;
    public static final int DEFAULT_TILE_SIZE = 192;
    public static final int AUTOMATIC_TRIANGLE_THRESHOLD = 20000;
    public static final int AUTOMATIC_PART_LIMIT = 64;

    private static final int MINIMUM_GRID_SIZE = 2;
    private static final int MAXIMUM_GRID_SIZE = 32;
    private static final int MINIMUM_TILE_SIZE = 16;
    private static final int MAXIMUM_TILE_SIZE = 1024;

    public static ImpostorImportSettings defaults() {
        return new ImpostorImportSettings(MODE_AUTOMATIC, DEFAULT_GRID_SIZE, DEFAULT_TILE_SIZE);
    }

    public static ImpostorImportSettings from(Map<String, Object> meta, AssetVariant variant) {
        return new ImpostorImportSettings(
                modeOf(setting(meta, variant, MODE_KEY)),
                clampedInteger(setting(meta, variant, GRID_SIZE_KEY), DEFAULT_GRID_SIZE,
                        MINIMUM_GRID_SIZE, MAXIMUM_GRID_SIZE),
                clampedInteger(setting(meta, variant, TILE_SIZE_KEY), DEFAULT_TILE_SIZE,
                        MINIMUM_TILE_SIZE, MAXIMUM_TILE_SIZE));
    }

    public boolean shouldBake(int triangleCount) {
        return shouldBake(triangleCount, 1);
    }

    public boolean shouldBake(int triangleCount, int partCount) {
        return switch (mode) {
            case MODE_ALWAYS -> true;
            case MODE_NEVER -> false;
            default -> triangleCount > AUTOMATIC_TRIANGLE_THRESHOLD && partCount <= AUTOMATIC_PART_LIMIT;
        };
    }

    public boolean exceedsAutomaticPartLimit(int partCount) {
        return mode.equals(MODE_AUTOMATIC) && partCount > AUTOMATIC_PART_LIMIT;
    }

    public int atlasSize() {
        return gridSize * tileSize;
    }

    private static String modeOf(Optional<String> declared) {
        return declared.map(ImpostorImportSettings::normalizeMode).orElse(MODE_AUTOMATIC);
    }

    private static String normalizeMode(String declared) {
        return switch (declared) {
            case MODE_ALWAYS, "true", "always", "yes" -> MODE_ALWAYS;
            case MODE_NEVER, "false", "never", "no" -> MODE_NEVER;
            default -> MODE_AUTOMATIC;
        };
    }

    private static int clampedInteger(Optional<String> declared, int fallback, int minimum, int maximum) {
        return declared.map(value -> parseInteger(value, fallback))
                .map(value -> Math.clamp(value, minimum, maximum))
                .orElse(fallback);
    }

    private static int parseInteger(String declared, int fallback) {
        try {
            return Integer.parseInt(declared.trim());
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    private static Optional<String> setting(Map<String, Object> meta, AssetVariant variant, String key) {
        return variant.value(key)
                .or(() -> Optional.ofNullable(meta.get(key)).map(ImpostorImportSettings::asText))
                .map(value -> value.toLowerCase(Locale.ROOT));
    }

    private static String asText(Object value) {
        return value instanceof String text ? text : String.valueOf(value);
    }
}
