package fr.epistudio.epysia.editor.preferences;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record ProfilerPreferences(boolean verticalSync, boolean frameRateCapEnabled,
                                  boolean viewportSupersampling, boolean shadowCaching,
                                  boolean shadowSplit, boolean depthPrepass,
                                  boolean instancing) {

    private static final String EPYSIA_DIRECTORY_NAME = ".epysia";
    private static final String PREFERENCES_FILENAME = "profiler.json";

    public static ProfilerPreferences defaults() {
        return new ProfilerPreferences(true, true, false, true, true, false, true);
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), EPYSIA_DIRECTORY_NAME, PREFERENCES_FILENAME);
    }

    public static ProfilerPreferences load(Path file) {
        if (!Files.isRegularFile(file)) {
            return defaults();
        }
        try {
            return fromJson(new JsonReader(Files.readString(file)).readRootObject());
        } catch (IOException | RuntimeException unreadable) {
            return defaults();
        }
    }

    private static ProfilerPreferences fromJson(Map<String, Object> root) {
        ProfilerPreferences base = defaults();
        return new ProfilerPreferences(
                boolOr(root, "verticalSync", base.verticalSync()),
                boolOr(root, "frameRateCapEnabled", base.frameRateCapEnabled()),
                boolOr(root, "viewportSupersampling", base.viewportSupersampling()),
                boolOr(root, "shadowCaching", base.shadowCaching()),
                boolOr(root, "shadowSplit", base.shadowSplit()),
                boolOr(root, "depthPrepass", base.depthPrepass()),
                boolOr(root, "instancing", base.instancing()));
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonWriter writer = new JsonWriter().beginObject()
                .key("verticalSync").valueBoolean(verticalSync)
                .key("frameRateCapEnabled").valueBoolean(frameRateCapEnabled)
                .key("viewportSupersampling").valueBoolean(viewportSupersampling)
                .key("shadowCaching").valueBoolean(shadowCaching)
                .key("shadowSplit").valueBoolean(shadowSplit)
                .key("depthPrepass").valueBoolean(depthPrepass)
                .key("instancing").valueBoolean(instancing)
                .endObject();
        Files.writeString(file, writer.toString());
    }

    private static boolean boolOr(Map<String, Object> root, String key, boolean fallback) {
        Object value = root.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
