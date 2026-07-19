package fr.epistudio.epysia.editor.preferences;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record EditorPreferences(float cameraSpeed, float cameraBoost,
                                boolean autosaveEnabled, int autosaveIntervalSeconds,
                                boolean gridVisible, boolean snapEnabled,
                                float overlayThickness, float gridFadeDistance) {

    public static final float MIN_OVERLAY_THICKNESS = 0.5f;
    public static final float MAX_OVERLAY_THICKNESS = 3.0f;
    public static final float MIN_GRID_FADE_DISTANCE = 10.0f;
    public static final float MAX_GRID_FADE_DISTANCE = 200.0f;
    public static final float DEFAULT_OVERLAY_THICKNESS = 1.0f;
    public static final float DEFAULT_GRID_FADE_DISTANCE = 40.0f;

    private static final String EPYSIA_DIRECTORY_NAME = ".epysia";
    private static final String PREFERENCES_FILENAME = "editor.json";
    private static final float DEFAULT_CAMERA_SPEED = 6.0f;
    private static final float DEFAULT_CAMERA_BOOST = 3.0f;
    private static final int DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 120;
    private static final float MIN_CAMERA_SPEED = 0.5f;
    private static final float MAX_CAMERA_SPEED = 100.0f;
    private static final float MIN_CAMERA_BOOST = 1.0f;
    private static final float MAX_CAMERA_BOOST = 20.0f;
    private static final int MIN_AUTOSAVE_INTERVAL_SECONDS = 10;
    private static final int MAX_AUTOSAVE_INTERVAL_SECONDS = 3600;

    public static EditorPreferences defaults() {
        return new EditorPreferences(DEFAULT_CAMERA_SPEED, DEFAULT_CAMERA_BOOST,
                false, DEFAULT_AUTOSAVE_INTERVAL_SECONDS, true, false,
                DEFAULT_OVERLAY_THICKNESS, DEFAULT_GRID_FADE_DISTANCE);
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), EPYSIA_DIRECTORY_NAME, PREFERENCES_FILENAME);
    }

    public static EditorPreferences load(Path file) {
        if (!Files.isRegularFile(file)) {
            return defaults();
        }
        try {
            Map<String, Object> root = new JsonReader(Files.readString(file)).readRootObject();
            return fromJson(root);
        } catch (IOException | RuntimeException error) {
            return defaults();
        }
    }

    private static EditorPreferences fromJson(Map<String, Object> root) {
        EditorPreferences base = defaults();
        return new EditorPreferences(
                clamp(floatOr(root, "cameraSpeed", base.cameraSpeed()), MIN_CAMERA_SPEED, MAX_CAMERA_SPEED),
                clamp(floatOr(root, "cameraBoost", base.cameraBoost()), MIN_CAMERA_BOOST, MAX_CAMERA_BOOST),
                boolOr(root, "autosaveEnabled", base.autosaveEnabled()),
                (int) clamp(floatOr(root, "autosaveIntervalSeconds", base.autosaveIntervalSeconds()),
                        MIN_AUTOSAVE_INTERVAL_SECONDS, MAX_AUTOSAVE_INTERVAL_SECONDS),
                boolOr(root, "gridVisible", base.gridVisible()),
                boolOr(root, "snapEnabled", base.snapEnabled()),
                clamp(floatOr(root, "overlayThickness", base.overlayThickness()),
                        MIN_OVERLAY_THICKNESS, MAX_OVERLAY_THICKNESS),
                clamp(floatOr(root, "gridFadeDistance", base.gridFadeDistance()),
                        MIN_GRID_FADE_DISTANCE, MAX_GRID_FADE_DISTANCE));
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonWriter writer = new JsonWriter().beginObject()
                .key("cameraSpeed").valueNumber(cameraSpeed)
                .key("cameraBoost").valueNumber(cameraBoost)
                .key("autosaveEnabled").valueBoolean(autosaveEnabled)
                .key("autosaveIntervalSeconds").valueNumber(autosaveIntervalSeconds)
                .key("gridVisible").valueBoolean(gridVisible)
                .key("snapEnabled").valueBoolean(snapEnabled)
                .key("overlayThickness").valueNumber(overlayThickness)
                .key("gridFadeDistance").valueNumber(gridFadeDistance)
                .endObject();
        Files.writeString(file, writer.toString());
    }

    public EditorPreferences withGridVisible(boolean visible) {
        return new EditorPreferences(cameraSpeed, cameraBoost, autosaveEnabled, autosaveIntervalSeconds,
                visible, snapEnabled, overlayThickness, gridFadeDistance);
    }

    public EditorPreferences withSnapEnabled(boolean enabled) {
        return new EditorPreferences(cameraSpeed, cameraBoost, autosaveEnabled, autosaveIntervalSeconds,
                gridVisible, enabled, overlayThickness, gridFadeDistance);
    }

    private static float floatOr(Map<String, Object> root, String key, float fallback) {
        return root.get(key) instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean boolOr(Map<String, Object> root, String key, boolean fallback) {
        return root.get(key) instanceof Boolean value ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
