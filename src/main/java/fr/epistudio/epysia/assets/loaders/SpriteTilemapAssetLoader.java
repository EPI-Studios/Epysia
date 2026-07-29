package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.NestedAssetPaths;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemapJsonCodec;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SpriteTilemapAssetLoader implements AssetLoader<SpriteTilemap> {

    public static final String EXTENSION = ".epytilemap";

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
    public SpriteTilemap load(EngineServices services, AssetLoadRequest request) {
        return loadedByPath.computeIfAbsent(request.uri().toString(),
                ignored -> readOrPlaceholder(services, request.uri()));
    }

    private SpriteTilemap readOrPlaceholder(EngineServices services, AssetUri uri) {
        try {
            return readResolved(services.assets().locator(), uri);
        } catch (EpysiaException failure) {
            services.logger().warn("Tilemap unavailable, using an empty placeholder: " + failure.getMessage());
            return new SpriteTilemap(0, 0);
        }
    }

    @Override
    public void dispose(EngineServices services, SpriteTilemap value) {
        loadedByPath.values().remove(value);
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    private SpriteTilemap readResolved(AssetLocator locator, AssetUri uri) {
        AssetSource source = locator.open(uri).orElseThrow(() ->
                new EpysiaException("Tilemap not found: " + uri));
        SpriteTilemap tilemap = codec.read(readText(source));
        return tilemap.setAtlasPath(NestedAssetPaths.rebase(uri, tilemap.atlasPath()));
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
