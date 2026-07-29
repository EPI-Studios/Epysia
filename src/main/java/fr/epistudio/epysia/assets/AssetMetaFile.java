package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AssetMetaFile {

    public static final String SUFFIX = ".epymeta";

    private static final String GUID_KEY = "guid";

    private AssetMetaFile() {
    }

    public static Map<String, Object> settingsFor(AssetLocator locator, AssetUri assetUri) {
        return locator.open(assetUri.withSuffix(SUFFIX))
                .flatMap(AssetMetaFile::readSource)
                .orElseGet(Map::of);
    }

    public static Map<String, Object> settingsOf(Path assetFile) {
        return readRoot(pathFor(assetFile));
    }

    private static Optional<Map<String, Object>> readSource(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = opened.get()) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(new JsonReader(text).readRootObject());
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public static boolean isMetaPath(String path) {
        return path.endsWith(SUFFIX);
    }

    public static Path pathFor(Path assetFile) {
        return assetFile.resolveSibling(assetFile.getFileName() + SUFFIX);
    }

    public static Optional<String> readGuid(Path metaFile) {
        return readString(metaFile, GUID_KEY);
    }

    public static Optional<String> readString(Path metaFile, String key) {
        return readRoot(metaFile).get(key) instanceof String value && !value.isBlank()
                ? Optional.of(value) : Optional.empty();
    }

    public static void writeGuid(Path metaFile, String guid) {
        writeString(metaFile, GUID_KEY, guid);
    }

    public static void writeString(Path metaFile, String key, String value) {
        Map<String, Object> root = new LinkedHashMap<>(readRoot(metaFile));
        root.put(key, value);
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

    public static boolean writeIfAbsent(Path metaFile, String key, String value) {
        if (readRoot(metaFile).containsKey(key)) {
            return false;
        }
        writeString(metaFile, key, value);
        return true;
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
