package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.project.Project;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class AssetScanner {

    public static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");

    private static final int MAXIMUM_SEARCH_DEPTH = 12;
    private static final String PROJECT_SUFFIX = ".project";

    private AssetScanner() {
    }

    public static List<AssetEntry> listDirectory(Path directory) throws IOException {
        List<AssetEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isRegularFile)) {
            for (Path path : stream) {
                toEntry(path).ifPresent(entries::add);
            }
        }
        return entries;
    }

    public static List<AssetEntry> searchRecursively(Path root) throws IOException {
        List<AssetEntry> entries = new ArrayList<>();
        collect(root, entries, 0);
        return entries;
    }

    private static void collect(Path directory, List<AssetEntry> entries, int depth) throws IOException {
        if (depth > MAXIMUM_SEARCH_DEPTH) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                collectOne(path, entries, depth);
            }
        }
    }

    private static void collectOne(Path path, List<AssetEntry> entries, int depth) throws IOException {
        String name = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            if (!name.startsWith(".") && !EXCLUDED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
                collect(path, entries, depth + 1);
            }
            return;
        }
        toEntry(path).ifPresent(entries::add);
    }

    public static List<Path> listSubdirectories(Path directory) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (!name.startsWith(".") && !EXCLUDED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
                    result.add(child);
                }
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return result;
    }

    private static Optional<AssetEntry> toEntry(Path path) {
        String name = path.getFileName().toString();
        if (isHidden(name)) {
            return Optional.empty();
        }
        return Optional.of(new AssetEntry(name, path.toAbsolutePath().toString(),
                classify(name), sizeOf(path), modifiedMillisOf(path)));
    }

    private static boolean isHidden(String name) {
        return name.startsWith(".") || name.endsWith(PROJECT_SUFFIX)
                || AssetMetaFile.isMetaPath(name) || name.equals(Project.MARKER_FILENAME);
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    private static long modifiedMillisOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    public static AssetType classify(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return AssetType.SCRIPT;
        }
        if (lower.endsWith(".glsl") || lower.endsWith(".vert") || lower.endsWith(".frag")) {
            return AssetType.SHADER;
        }
        if (lower.endsWith(".epyprefab")) {
            return AssetType.PREFAB;
        }
        if (lower.endsWith(".epyscene")) {
            return AssetType.SCENE;
        }
        if (lower.endsWith(".epygraph")) {
            return AssetType.GRAPH;
        }
        if (lower.endsWith(".epymaterial")) {
            return AssetType.MATERIAL;
        }
        return classifyBinary(lower);
    }

    private static AssetType classifyBinary(String lower) {
        if (lower.endsWith(".obj") || lower.endsWith(".epymesh")) {
            return AssetType.MESH;
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".tga") || lower.endsWith(".bmp")) {
            return AssetType.TEXTURE;
        }
        if (lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".mp3") || lower.endsWith(".flac")) {
            return AssetType.AUDIO;
        }
        return AssetType.OTHER;
    }
}
