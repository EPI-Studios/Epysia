package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.NestedAssetPaths;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.scene.serialization.MaterialJsonCodec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MaterialAssetLoader implements AssetLoader<Material> {

    public static final String EXTENSION = ".epymaterial";

    private final MaterialJsonCodec codec = new MaterialJsonCodec();
    private final Map<String, Material> loadedByPath = new HashMap<>();

    @Override
    public Class<Material> assetType() {
        return Material.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public Material load(EngineServices services, AssetLoadRequest request) {
        AssetLocator locator = services.assets().locator();
        Material material = loadedByPath.computeIfAbsent(request.uri().toString(),
                ignored -> readResolved(locator, request.uri()));
        MaterialFields.resolveTextures(material, services.assets());
        return material;
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    Material loadFromFile(AssetLocator locator, Path file) {
        AssetUri uri = locator.fromFile(file);
        return loadedByPath.computeIfAbsent(uri.toString(),
                ignored -> decode(readFileText(file), uri));
    }

    private Material readResolved(AssetLocator locator, AssetUri uri) {
        AssetSource source = locator.open(uri).orElseThrow(() ->
                new EpysiaException("Material asset not found: " + uri));
        return decode(readText(source), uri);
    }

    private Material decode(String json, AssetUri origin) {
        Material material = codec.readSingle(json)
                .orElseThrow(() -> new EpysiaException("Not a material document: " + origin));
        material.setAssetPath(origin.toString());
        rebaseRelativeTexturePaths(material, origin);
        return material;
    }

    private static void rebaseRelativeTexturePaths(Material material, AssetUri origin) {
        for (Map.Entry<String, String> entry : Map.copyOf(material.texturePaths()).entrySet()) {
            material.setTexturePath(entry.getKey(), NestedAssetPaths.rebase(origin, entry.getValue()));
        }
    }

    private static String readFileText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read material asset " + file + ": " + exception.getMessage());
        }
    }

    private static String readText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Material asset not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
