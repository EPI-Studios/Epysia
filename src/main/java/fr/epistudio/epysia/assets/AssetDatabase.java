package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class AssetDatabase {

    private static final String INDEX_DIRECTORY = ".epysia";
    private static final String INDEX_FILE = "assets.json";

    private final Path projectRoot;
    private final Path indexPath;
    private final Map<String, String> pathByGuid = new HashMap<>();
    private final Map<String, String> guidByPath = new HashMap<>();

    private AssetDatabase(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.indexPath = projectRoot.resolve(INDEX_DIRECTORY).resolve(INDEX_FILE);
    }

    public static AssetDatabase open(Path projectRoot) {
        AssetDatabase database = new AssetDatabase(projectRoot);
        database.loadIndex();
        database.refresh();
        return database;
    }

    public Optional<String> pathForGuid(String guid) {
        return Optional.ofNullable(pathByGuid.get(guid));
    }

    public Optional<String> guidForPath(String path) {
        return Optional.ofNullable(guidByPath.get(path));
    }

    public void refresh() {
        guidByPath.clear();
        pathByGuid.clear();
        for (String path : scanAssetPaths()) {
            String guid = resolveGuid(path);
            guidByPath.put(path, guid);
            pathByGuid.put(guid, path);
        }
        saveIndex();
    }

    private String resolveGuid(String path) {
        Path metaFile = AssetMetaFile.pathFor(projectRoot.resolve(path));
        Optional<String> declared = AssetMetaFile.readGuid(metaFile);
        if (declared.isPresent() && !pathByGuid.containsKey(declared.get())) {
            return declared.get();
        }
        String guid = UUID.randomUUID().toString();
        AssetMetaFile.writeGuid(metaFile, guid);
        return guid;
    }

    private List<String> scanAssetPaths() {
        if (!Files.isDirectory(projectRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(projectRoot)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(projectRoot::relativize)
                    .map(AssetDatabase::normalize)
                    .filter(path -> !path.startsWith(INDEX_DIRECTORY + "/"))
                    .filter(path -> !path.startsWith("."))
                    .filter(path -> !AssetMetaFile.isMetaPath(path))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String normalize(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private void loadIndex() {
        if (!Files.isRegularFile(indexPath)) {
            return;
        }
        try {
            parseIndex(Files.readString(indexPath));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseIndex(String text) {
        Map<String, Object> root = new JsonReader(text).readRootObject();
        List<Object> entries = (List<Object>) root.getOrDefault("assets", List.of());
        for (Object element : entries) {
            if (element instanceof Map<?, ?> entry
                    && entry.get("guid") instanceof String guid
                    && entry.get("path") instanceof String path) {
                pathByGuid.put(guid, path);
                guidByPath.put(path, guid);
            }
        }
    }

    private void saveIndex() {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("assets").beginArray();
        for (Map.Entry<String, String> entry : guidByPath.entrySet()) {
            writer.beginObject();
            writer.key("guid").valueString(entry.getValue());
            writer.key("path").valueString(entry.getKey());
            writer.endObject();
        }
        writer.endArray();
        writer.endObject();
        writeIndexFile(writer.toString());
    }

    private void writeIndexFile(String content) {
        try {
            Files.createDirectories(indexPath.getParent());
            Files.writeString(indexPath, content);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
