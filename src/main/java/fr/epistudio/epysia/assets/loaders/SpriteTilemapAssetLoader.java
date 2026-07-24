package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemapJsonCodec;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SpriteTilemapAssetLoader implements AssetLoader<SpriteTilemap> {

    public static final String EXTENSION = ".epytilemap";

    private static final String CLASSPATH_ROOT = "tilemaps/";

    private final SpriteTilemapJsonCodec codec = new SpriteTilemapJsonCodec();
    private final Map<String, SpriteTilemap> loadedByPath = new HashMap<>();

    @Override
    public Class<SpriteTilemap> assetType() {
        return SpriteTilemap.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public SpriteTilemap load(EngineServices services, String path) {
        return loadedByPath.computeIfAbsent(path, this::readResolved);
    }

    @Override
    public void dispose(EngineServices services, SpriteTilemap value) {
        loadedByPath.values().remove(value);
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    private SpriteTilemap readResolved(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Tilemap not found on filesystem or classpath: " + path));
        return rebaseAtlasPath(codec.read(readText(source)), path);
    }

    private static SpriteTilemap rebaseAtlasPath(SpriteTilemap tilemap, String origin) {
        Path originPath = Path.of(origin);
        if (tilemap.atlasPath().isEmpty() || !Files.isRegularFile(originPath)) {
            return tilemap;
        }
        String storedPath = tilemap.atlasPath();
        String unprefixedPath = TexturePathPrefixes.stripPrefixes(storedPath);
        String prefix = storedPath.substring(0, storedPath.length() - unprefixedPath.length());
        Path atlasPath = Path.of(unprefixedPath);
        if (atlasPath.isAbsolute()) {
            return tilemap;
        }
        Path baseDirectory = originPath.toAbsolutePath().getParent();
        return tilemap.setAtlasPath(prefix + baseDirectory.resolve(atlasPath).normalize());
    }

    private static String readText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Tilemap not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
