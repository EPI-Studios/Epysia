package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AssetMetaFile {

    public static final String SUFFIX = ".epymeta";
    public static final String FILTER_KEY = "filter";

    private static final String GUID_KEY = "guid";

    private AssetMetaFile() {
    }

    public static boolean isMetaPath(String path) {
        return path.endsWith(SUFFIX);
    }

    public static Path pathFor(Path assetFile) {
        return assetFile.resolveSibling(assetFile.getFileName() + SUFFIX);
    }

    public static Optional<String> readGuid(Path metaFile) {
        if (!Files.isRegularFile(metaFile)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = new JsonReader(Files.readString(metaFile)).readRootObject();
            return root.get(GUID_KEY) instanceof String guid && !guid.isBlank()
                    ? Optional.of(guid) : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public static void writeGuid(Path metaFile, String guid) {
        Map<String, Object> root = new LinkedHashMap<>(readRoot(metaFile));
        root.put(GUID_KEY, guid);
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            writeScalar(writer, entry.getKey(), entry.getValue());
        }
        writer.endObject();
        try {
            Files.createDirectories(metaFile.getParent());
            Files.writeString(metaFile, writer.toString());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static Map<String, Object> readRoot(Path metaFile) {
        if (!Files.isRegularFile(metaFile)) {
            return Map.of();
        }
        try {
            return new JsonReader(Files.readString(metaFile)).readRootObject();
        } catch (IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }

    private static void writeScalar(JsonWriter writer, String key, Object value) {
        switch (value) {
            case String text -> writer.key(key).valueString(text);
            case Boolean flag -> writer.key(key).valueBoolean(flag);
            case Long number -> writer.key(key).valueNumber(number);
            case Double number -> writer.key(key).valueNumber((float) (double) number);
            default -> {
            }
        }
    }

    public static void moveAlongside(Path fromAsset, Path toAsset) throws IOException {
        Path fromMeta = pathFor(fromAsset);
        if (Files.isRegularFile(fromMeta)) {
            Files.move(fromMeta, pathFor(toAsset));
        }
    }

    public static void deleteAlongside(Path assetFile) throws IOException {
        Files.deleteIfExists(pathFor(assetFile));
    }
}
