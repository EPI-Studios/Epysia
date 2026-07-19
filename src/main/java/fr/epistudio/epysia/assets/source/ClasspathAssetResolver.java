package fr.epistudio.epysia.assets.source;

import java.util.Optional;

public final class ClasspathAssetResolver implements AssetResolver {

    private final String root;
    private final String base;

    public ClasspathAssetResolver(String root, String base) {
        this.root = root;
        this.base = base;
    }

    @Override
    public Optional<AssetSource> resolve(String name) {
        String logicalPath = join(join(root, base), name);
        return Optional.of(new ClasspathAssetSource(logicalPath));
    }

    @Override
    public AssetResolver relativeTo(String subDirectory) {
        return new ClasspathAssetResolver(root, join(base, subDirectory));
    }

    private static String join(String left, String right) {
        if (left == null || left.isEmpty()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        String trimmed = left.endsWith("/") ? left.substring(0, left.length() - 1) : left;
        return trimmed + "/" + right;
    }
}
