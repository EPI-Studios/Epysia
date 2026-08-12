package fr.epistudio.epysia.settings;

import fr.epistudio.epysia.render.GraphicsApi;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record GameSettings(GraphicsApi renderApi, String gpuAdapter,
                           int windowWidth, int windowHeight, boolean fullscreen,
                           boolean verticalSync, int frameRateCap,
                           String logLevel, boolean showFrameRate) {

    public static final String FILE_NAME = "epysia-settings.json";
    public static final String SYSTEM_DEFAULT_ADAPTER = "system-default";

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;
    private static final int MINIMUM_DIMENSION = 320;
    private static final int MAXIMUM_DIMENSION = 15_360;
    private static final int MAXIMUM_FRAME_CAP = 1000;
    private static final String DEFAULT_LOG_LEVEL = "info";

    public static GameSettings defaults() {
        return new GameSettings(GraphicsApi.OPENGL, SYSTEM_DEFAULT_ADAPTER,
                DEFAULT_WIDTH, DEFAULT_HEIGHT, false, true, 0, DEFAULT_LOG_LEVEL, false);
    }

    public static Path fileIn(Path gameDirectory) {
        return gameDirectory.resolve(FILE_NAME);
    }

    public static GameSettings load(Path file) {
        if (!Files.isRegularFile(file)) {
            return defaults();
        }
        try {
            return fromJson(new JsonReader(Files.readString(file)).readRootObject());
        } catch (IOException | RuntimeException unreadable) {
            return defaults();
        }
    }

    private static GameSettings fromJson(Map<String, Object> root) {
        GameSettings base = defaults();
        return new GameSettings(
                GraphicsApi.parse(stringOr(root, "renderApi", base.renderApi().id()), base.renderApi()),
                stringOr(root, "gpuAdapter", base.gpuAdapter()),
                clampDimension(intOr(root, "windowWidth", base.windowWidth())),
                clampDimension(intOr(root, "windowHeight", base.windowHeight())),
                boolOr(root, "fullscreen", base.fullscreen()),
                boolOr(root, "verticalSync", base.verticalSync()),
                Math.clamp(intOr(root, "frameRateCap", base.frameRateCap()), 0, MAXIMUM_FRAME_CAP),
                stringOr(root, "logLevel", base.logLevel()),
                boolOr(root, "showFrameRate", base.showFrameRate())).clamped();
    }

    public GameSettings clamped() {
        return new GameSettings(renderApi == null ? GraphicsApi.OPENGL : renderApi,
                gpuAdapter == null || gpuAdapter.isBlank() ? SYSTEM_DEFAULT_ADAPTER : gpuAdapter,
                clampDimension(windowWidth), clampDimension(windowHeight), fullscreen, verticalSync,
                Math.clamp(frameRateCap, 0, MAXIMUM_FRAME_CAP),
                logLevel == null || logLevel.isBlank() ? DEFAULT_LOG_LEVEL : logLevel,
                showFrameRate);
    }

    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonWriter writer = new JsonWriter().beginObject()
                .key("renderApi").valueString(renderApi.id())
                .key("gpuAdapter").valueString(gpuAdapter)
                .key("windowWidth").valueNumber(windowWidth)
                .key("windowHeight").valueNumber(windowHeight)
                .key("fullscreen").valueBoolean(fullscreen)
                .key("verticalSync").valueBoolean(verticalSync)
                .key("frameRateCap").valueNumber(frameRateCap)
                .key("logLevel").valueString(logLevel)
                .key("showFrameRate").valueBoolean(showFrameRate)
                .endObject();
        Files.writeString(file, writer.toString());
    }

    private static int clampDimension(int value) {
        return Math.clamp(value, MINIMUM_DIMENSION, MAXIMUM_DIMENSION);
    }

    private static String stringOr(Map<String, Object> root, String key, String fallback) {
        return root.get(key) instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int intOr(Map<String, Object> root, String key, int fallback) {
        return root.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean boolOr(Map<String, Object> root, String key, boolean fallback) {
        return root.get(key) instanceof Boolean flag ? flag : fallback;
    }
}
