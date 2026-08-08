package fr.epistudio.epysia.editor.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ProjectAssetReachability {

    private static final String ASSET_ROOT = "assets";
    private static final long MAXIMUM_SCANNED_BYTES = 4L * 1024L * 1024L;
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9_./\\\\-]{3,}");
    private static final Set<String> DESCRIBING_EXTENSIONS = Set.of(
            ".epyscene", ".epyprefab", ".epymaterial", ".epygraph", ".epyui", ".epyimpostor",
            ".epymeta", ".project", ".glsl", ".java", ".kt", ".json", ".txt", ".properties");

    private ProjectAssetReachability() {
    }

    static Set<String> collect(Path root) throws IOException {
        Set<String> tokens = new HashSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path candidate : walk.toList()) {
                if (describesReferences(root, candidate)) {
                    harvest(candidate, tokens);
                }
            }
        }
        return tokens;
    }

    static boolean shipsWith(Path root, Path source, Set<String> reachable) {
        Path relative = root.relativize(source);
        if (relative.getNameCount() == 0 || Files.isDirectory(source)) {
            return true;
        }
        if (!relative.getName(0).toString().equalsIgnoreCase(ASSET_ROOT)) {
            return true;
        }
        return isReachable(relative, reachable);
    }

    private static boolean isReachable(Path relative, Set<String> reachable) {
        String posix = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (reachable.contains(posix) || reachable.contains(stripMeta(posix))) {
            return true;
        }
        for (int index = 0; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString().toLowerCase(Locale.ROOT);
            if (reachable.contains(segment) || reachable.contains(stripMeta(segment))) {
                return true;
            }
        }
        return false;
    }

    private static String stripMeta(String value) {
        return value.endsWith(".epymeta") ? value.substring(0, value.length() - ".epymeta".length()) : value;
    }

    private static boolean describesReferences(Path root, Path candidate) {
        if (!Files.isRegularFile(candidate)) {
            return false;
        }
        Path relative = root.relativize(candidate);
        if (relative.getName(0).toString().equalsIgnoreCase(ASSET_ROOT)
                && !candidate.toString().toLowerCase(Locale.ROOT).endsWith(".epymeta")) {
            return false;
        }
        String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && DESCRIBING_EXTENSIONS.contains(name.substring(dot));
    }

    private static void harvest(Path file, Set<String> tokens) throws IOException {
        if (Files.size(file) > MAXIMUM_SCANNED_BYTES) {
            return;
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Matcher matcher = REFERENCE.matcher(text);
        while (matcher.find()) {
            addToken(tokens, matcher.group().toLowerCase(Locale.ROOT));
        }
    }

    private static void addToken(Set<String> tokens, String raw) {
        String value = raw.replace('\\', '/');
        int assets = value.indexOf(ASSET_ROOT + "/");
        if (assets >= 0) {
            tokens.add(value.substring(assets));
        }
        tokens.add(value);
        List<String> segments = List.of(value.split("/"));
        for (String segment : segments) {
            if (!segment.isBlank()) {
                tokens.add(segment);
            }
        }
    }
}
