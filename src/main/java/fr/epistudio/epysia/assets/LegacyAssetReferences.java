package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.assets.loaders.TextureImportSettings;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LegacyAssetReferences {

    private static final Map<String, Map.Entry<String, String>> PREFIXES = Map.of(
            "point:", Map.entry(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_POINT),
            "linear:", Map.entry(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_LINEAR),
            "clamp:", Map.entry(TextureImportSettings.WRAP_KEY, TextureImportSettings.WRAP_CLAMP),
            "mirror:", Map.entry(TextureImportSettings.WRAP_KEY, TextureImportSettings.WRAP_MIRROR),
            "srgb:", Map.entry(TextureImportSettings.COLOR_SPACE_KEY, TextureImportSettings.COLOR_SPACE_SRGB));

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:[\\\\/]");

    private LegacyAssetReferences() {
    }

    public static AssetUri interpret(String storedPath, AssetRegistry registry) {
        if (storedPath == null || storedPath.isEmpty()) {
            return AssetUri.empty();
        }
        Optional<AssetUri> alreadyCanonical = AssetUri.parse(storedPath);
        if (alreadyCanonical.isPresent()) {
            return alreadyCanonical.get();
        }
        Stripped stripped = strip(storedPath);
        AssetUri uri = locate(stripped.remainder(), registry.locator());
        migrateSettings(uri, stripped.settings(), registry);
        return uri;
    }

    public static AssetUri interpretWithoutMigration(String storedPath, AssetLocator locator) {
        if (storedPath == null || storedPath.isEmpty()) {
            return AssetUri.empty();
        }
        return AssetUri.parse(storedPath)
                .orElseGet(() -> locate(strip(storedPath).remainder(), locator));
    }

    public static String stripPrefixes(String storedPath) {
        return storedPath == null ? "" : strip(storedPath).remainder();
    }

    private static Stripped strip(String storedPath) {
        Map<String, String> settings = new LinkedHashMap<>();
        String remainder = storedPath;
        boolean matched = true;
        while (matched) {
            matched = false;
            for (Map.Entry<String, Map.Entry<String, String>> prefix : PREFIXES.entrySet()) {
                if (remainder.startsWith(prefix.getKey())) {
                    settings.putIfAbsent(prefix.getValue().getKey(), prefix.getValue().getValue());
                    remainder = remainder.substring(prefix.getKey().length());
                    matched = true;
                }
            }
        }
        return new Stripped(remainder, settings);
    }

    private static AssetUri locate(String remainder, AssetLocator locator) {
        Optional<AssetUri> canonical = AssetUri.parse(remainder)
                .filter(parsed -> parsed.scheme() != AssetScheme.NONE);
        if (canonical.isPresent()) {
            return canonical.get();
        }
        if (looksAbsolute(remainder)) {
            return locateAbsolute(remainder, locator);
        }
        AssetUri projectCandidate = AssetUri.project(remainder);
        return locator.exists(projectCandidate) ? projectCandidate : AssetUri.engine(remainder);
    }

    public static boolean looksAbsolute(String path) {
        boolean absoluteHere = asPath(path).filter(Path::isAbsolute).isPresent();
        return absoluteHere || WINDOWS_DRIVE.matcher(path).lookingAt() || path.startsWith("/");
    }

    private static AssetUri locateAbsolute(String remainder, AssetLocator locator) {
        Optional<AssetUri> onThisMachine = asPath(remainder)
                .filter(Path::isAbsolute)
                .map(locator::fromFile);
        return onThisMachine
                .filter(locator::exists)
                .or(() -> locator.trailingPathInProject(remainder))
                .or(() -> onThisMachine)
                .orElseGet(() -> new AssetUri(AssetScheme.SYSTEM, remainder));
    }

    private static Optional<Path> asPath(String remainder) {
        try {
            return Optional.of(Path.of(remainder));
        } catch (InvalidPathException malformed) {
            return Optional.empty();
        }
    }

    private static void migrateSettings(AssetUri uri, Map<String, String> settings, AssetRegistry registry) {
        if (settings.isEmpty() || uri.scheme() != AssetScheme.PROJECT) {
            return;
        }
        registry.locator().file(uri).ifPresent(assetFile -> {
            Path metaFile = AssetMetaFile.pathFor(assetFile);
            for (Map.Entry<String, String> setting : settings.entrySet()) {
                reportMigration(uri, setting, AssetMetaFile.writeIfAbsent(metaFile, setting.getKey(),
                        setting.getValue()), registry);
            }
        });
    }

    private static void reportMigration(AssetUri uri, Map.Entry<String, String> setting,
                                        boolean written, AssetRegistry registry) {
        if (written) {
            registry.logger().info("[assets] moved legacy " + setting.getKey() + "="
                    + setting.getValue() + " into the import settings of " + uri);
            return;
        }
        registry.logger().warn("[assets] legacy " + setting.getKey() + "=" + setting.getValue()
                + " ignored for " + uri + ", its import settings already declare that key");
    }

    private record Stripped(String remainder, Map<String, String> settings) {
    }
}
