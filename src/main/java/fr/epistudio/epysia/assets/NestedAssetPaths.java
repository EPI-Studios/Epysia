package fr.epistudio.epysia.assets;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public final class NestedAssetPaths {

    private NestedAssetPaths() {
    }

    public static String rebase(AssetUri origin, String storedPath) {
        if (storedPath == null || storedPath.isEmpty()) {
            return "";
        }
        Optional<AssetUri> canonical = AssetUri.parse(storedPath)
                .filter(candidate -> candidate.scheme() != AssetScheme.NONE);
        if (canonical.isPresent()) {
            return canonical.get().toString();
        }
        String remainder = LegacyAssetReferences.stripPrefixes(storedPath);
        String prefix = storedPath.substring(0, storedPath.length() - remainder.length());
        if (isAbsolute(remainder)) {
            return storedPath;
        }
        return prefix + origin.resolveRelative(remainder);
    }

    private static boolean isAbsolute(String candidate) {
        try {
            return Path.of(candidate).isAbsolute();
        } catch (InvalidPathException malformed) {
            return false;
        }
    }
}
