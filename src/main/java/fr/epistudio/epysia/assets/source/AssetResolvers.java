package fr.epistudio.epysia.assets.source;

import java.nio.file.Path;
import java.util.Optional;

public final class AssetResolvers {

    private AssetResolvers() {
    }

    public static ResolvedLocation forPath(String path, String classpathRoot) {
        Path candidate = Path.of(path);
        if (candidate.isAbsolute()) {
            Path parent = candidate.getParent();
            Path base = parent != null ? parent : candidate.getRoot();
            return new ResolvedLocation(new FilesystemAssetResolver(base), candidate.getFileName().toString());
        }
        String normalized = path.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String parentDirectory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        String leaf = lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
        return new ResolvedLocation(new ClasspathAssetResolver(classpathRoot, parentDirectory), leaf);
    }

    public record ResolvedLocation(AssetResolver directory, String leafName) {

        public Optional<AssetSource> source() {
            return directory.resolve(leafName);
        }
    }
}
