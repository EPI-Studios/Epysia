package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.NestedAssetPaths;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SpriteAtlasAssetLoader implements AssetLoader<SpriteAtlas> {

    public static final String EXTENSION = ".epyatlas";

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
    public SpriteAtlas load(EngineServices services, AssetLoadRequest request) {
        AssetLocator locator = services.assets().locator();
        return loadedByPath.computeIfAbsent(request.uri().toString(),
                ignored -> readResolved(locator, request.uri()));
    }

    @Override
    public void dispose(EngineServices services, SpriteAtlas value) {
        loadedByPath.values().remove(value);
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    private SpriteAtlas readResolved(AssetLocator locator, AssetUri uri) {
        AssetSource source = locator.open(uri).orElseThrow(() ->
                new EpysiaException("Sprite atlas not found: " + uri));
        SpriteAtlas atlas = codec.read(readText(source));
        return atlas.withTexturePath(NestedAssetPaths.rebase(uri, atlas.texturePath()));
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
