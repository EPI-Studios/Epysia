package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class AssetMetaFile {

    public static final String SUFFIX = ".epymeta";

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
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key(GUID_KEY).valueString(guid);
        writer.endObject();
        try {
            Files.createDirectories(metaFile.getParent());
            Files.writeString(metaFile, writer.toString());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
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
