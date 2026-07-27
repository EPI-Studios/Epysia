package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SpriteAtlasAssetLoader implements AssetLoader<SpriteAtlas> {

    public static final String EXTENSION = ".epyatlas";

    private static final String CLASSPATH_ROOT = "atlases/";

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();
    private final Map<String, SpriteAtlas> loadedByPath = new HashMap<>();

    @Override
    public Class<SpriteAtlas> assetType() {
        return SpriteAtlas.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public SpriteAtlas load(EngineServices services, String path) {
        return loadedByPath.computeIfAbsent(path, this::readResolved);
    }

    @Override
    public void dispose(EngineServices services, SpriteAtlas value) {
        loadedByPath.values().remove(value);
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    private SpriteAtlas readResolved(String path) {
        String assetPath = TexturePathPrefixes.stripPrefixes(path);
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(assetPath, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Sprite atlas not found on filesystem or classpath: " + assetPath));
        return rebaseTexturePath(codec.read(readText(source)), assetPath);
    }

    private static SpriteAtlas rebaseTexturePath(SpriteAtlas atlas, String origin) {
        if (atlas.texturePath().isEmpty()) {
            return atlas;
        }
        String storedPath = atlas.texturePath();
        String unprefixedPath = TexturePathPrefixes.stripPrefixes(storedPath);
        String prefix = storedPath.substring(0, storedPath.length() - unprefixedPath.length());
        if (Path.of(unprefixedPath).isAbsolute()) {
            return atlas;
        }
        String parentDirectory = parentDirectoryOf(origin);
        String rebased = parentDirectory.isEmpty() ? unprefixedPath : parentDirectory + "/" + unprefixedPath;
        return atlas.withTexturePath(prefix + rebased);
    }

    private static String parentDirectoryOf(String origin) {
        String normalized = origin.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
    }

    private static String readText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Sprite atlas not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
