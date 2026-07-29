package fr.epistudio.epysia.assets;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public record AssetUri(AssetScheme scheme, String path) {

    private static final AssetUri EMPTY = new AssetUri(AssetScheme.NONE, "");

    public AssetUri {
        path = canonical(scheme, path);
    }

    public static AssetUri empty() {
        return EMPTY;
    }

    public static AssetUri project(String relativePath) {
        return new AssetUri(AssetScheme.PROJECT, relativePath);
    }

    public static AssetUri engine(String relativePath) {
        return new AssetUri(AssetScheme.ENGINE, relativePath);
    }

    public static AssetUri system(Path file) {
        return new AssetUri(AssetScheme.SYSTEM, file.toAbsolutePath().toString());
    }

    public static Optional<AssetUri> parse(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.of(EMPTY);
        }
        for (AssetScheme candidate : AssetScheme.values()) {
            if (candidate != AssetScheme.NONE && text.startsWith(candidate.prefix())) {
                return Optional.of(new AssetUri(candidate, text.substring(candidate.prefix().length())));
            }
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return scheme == AssetScheme.NONE || path.isEmpty();
    }

    public String fileName() {
        int lastSeparator = path.lastIndexOf('/');
        return lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
    }

    public Optional<AssetUri> parent() {
        int lastSeparator = path.lastIndexOf('/');
        if (lastSeparator <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AssetUri(scheme, path.substring(0, lastSeparator)));
    }

    public AssetUri sibling(String otherFileName) {
        return parent()
                .map(directory -> new AssetUri(scheme, directory.path() + "/" + otherFileName))
                .orElseGet(() -> new AssetUri(scheme, otherFileName));
    }

    public AssetUri resolveRelative(String relativePath) {
        Optional<AssetUri> absolute = parse(relativePath)
                .filter(candidate -> candidate.scheme() != AssetScheme.NONE);
        if (absolute.isPresent()) {
            return absolute.get();
        }
        String directory = parent().map(AssetUri::path).orElse("");
        String combined = directory.isEmpty() ? relativePath : directory + "/" + relativePath;
        return new AssetUri(scheme, flatten(combined));
    }

    private static String flatten(String combined) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : combined.replace('\\', '/').split("/")) {
            if (segment.equals("..")) {
                segments.pollLast();
            } else if (!segment.isEmpty() && !segment.equals(".")) {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    public AssetUri withSuffix(String suffix) {
        return new AssetUri(scheme, path + suffix);
    }

    @Override
    public String toString() {
        return isEmpty() ? "" : scheme.prefix() + path;
    }

    private static String canonical(AssetScheme scheme, String rawPath) {
        String forwardSlashed = rawPath == null ? "" : rawPath.replace('\\', '/');
        if (scheme == AssetScheme.SYSTEM) {
            return forwardSlashed;
        }
        String withoutLeadingCurrent = forwardSlashed.startsWith("./")
                ? forwardSlashed.substring(2) : forwardSlashed;
        return withoutLeadingCurrent.startsWith("/")
                ? withoutLeadingCurrent.substring(1) : withoutLeadingCurrent;
    }
}
