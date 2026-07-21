package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.material.Material;
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

    private static final String CLASSPATH_ROOT = "materials/";

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
    public Material load(EngineServices services, String path) {
        return loadedByPath.computeIfAbsent(path, this::readResolved);
    }

    Material loadFromFile(Path file) {
        String key = file.toAbsolutePath().normalize().toString();
        return loadedByPath.computeIfAbsent(key, ignored -> decode(readFileText(file), key));
    }

    private Material readResolved(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Material asset not found on filesystem or classpath: " + path));
        return decode(readText(source), path);
    }

    private Material decode(String json, String origin) {
        return codec.readSingle(json).map(material -> material.setAssetPath(origin))
                .orElseThrow(() -> new EpysiaException("Not a material document: " + origin));
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
