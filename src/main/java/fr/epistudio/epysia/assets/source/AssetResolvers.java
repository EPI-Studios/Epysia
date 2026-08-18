package fr.epistudio.epysia.assets.source;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AssetResolvers {

    private static Optional<Path> asPath(String path) {
        try {
            return Optional.of(Path.of(path));
        } catch (InvalidPathException unusableOnThisFilesystem) {
            return Optional.empty();
        }
    }

    private static final String PROJECT_PREFIX = "res://";

    private static final AtomicReference<Path> projectRoot = new AtomicReference<>();

    private AssetResolvers() {
    }

    public static void useProjectRoot(Path root) {
        projectRoot.set(root);
    }

    public static Optional<Path> projectRoot() {
        return Optional.ofNullable(projectRoot.get());
    }

    public static ResolvedLocation forPath(String path, String classpathRoot) {
        Optional<Path> withinProject = resolveWithinProject(path);
        if (withinProject.isPresent()) {
            return absoluteLocation(withinProject.get());
        }
        Optional<Path> candidate = asPath(path);
        if (candidate.isPresent() && candidate.get().isAbsolute()) {
            return absoluteLocation(candidate.get());
        }
        String normalized = path.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String parentDirectory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        String leaf = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        return new ResolvedLocation(new ClasspathAssetResolver(classpathRoot, parentDirectory), leaf);
    }

    private static Optional<Path> resolveWithinProject(String path) {
        if (!path.startsWith(PROJECT_PREFIX)) {
            return Optional.empty();
        }
        return projectRoot().map(root -> root.resolve(path.substring(PROJECT_PREFIX.length())));
    }

    private static ResolvedLocation absoluteLocation(Path file) {
        Path parent = file.getParent();
        Path base = parent != null ? parent : file.getRoot();
        return new ResolvedLocation(new FilesystemAssetResolver(base), file.getFileName().toString());
    }

    public record ResolvedLocation(AssetResolver directory, String leafName) {

        public Optional<AssetSource> source() {
            return directory.resolve(leafName);
        }
    }
}
