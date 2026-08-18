package fr.epistudio.epysia.editor.assets;

import java.util.List;
import java.util.Locale;

public final class AssetFileNames {

    public static final List<String> KNOWN_EXTENSIONS = List.of(
            ".surf.glsl", ".fog.glsl", ".post.glsl", ".vert.glsl", ".frag.glsl", ".comp.glsl", ".glsl",
            ".epymaterial", ".epygraph", ".epyscene", ".epyprefab", ".epyinstances", ".java",
            ".epynoise", ".epygradient", ".epycurve");

    private static final List<String> WATCHED_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".tga", ".bmp", ".hdr", ".ktx2", ".dds",
            ".gltf", ".glb", ".obj", ".fbx", ".epymesh", ".wav", ".ogg", ".mp3");
    private static final List<String> IGNORED_DIRECTORIES = List.of(
            ".epysia", ".git", ".gradle", "build", "out");

    private AssetFileNames() {
    }

    public static boolean isWatchable(java.nio.file.Path file) {
        for (java.nio.file.Path part : file) {
            if (IGNORED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : WATCHED_EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.indexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }

    public static boolean hasExtension(String fileName, String extension) {
        return !extension.isEmpty()
                && fileName.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT));
    }

    public static String withExtension(String requested, String extension) {
        return hasExtension(requested, extension) ? requested : requested + extension;
    }

    public static String withoutExtension(String requested, String extension) {
        return hasExtension(requested, extension)
                ? requested.substring(0, requested.length() - extension.length())
                : requested;
    }

    public static String baseName(String requested) {
        String name = requested;
        for (String extension : KNOWN_EXTENSIONS) {
            String stripped = withoutExtension(name, extension);
            if (!stripped.equals(name)) {
                return stripped;
            }
        }
        return name;
    }
}
