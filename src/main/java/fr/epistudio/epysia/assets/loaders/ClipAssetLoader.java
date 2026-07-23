package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epyclip.EpyClipReader;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClipAssetLoader implements AssetLoader<Clip> {

    public static final String EXTENSION = ".epyclip";

    private static final String CLASSPATH_ROOT = "clips/";

    private final Map<String, Clip> loadedByPath = new HashMap<>();

    @Override
    public Class<Clip> assetType() {
        return Clip.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public Clip load(EngineServices services, String path) {
        return loadedByPath.computeIfAbsent(path, this::readResolved);
    }

    @Override
    public void unloadAll() {
        loadedByPath.clear();
    }

    private Clip readResolved(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Clip asset not found on filesystem or classpath: " + path));
        return EpyClipReader.read(readBytes(source));
    }

    private static byte[] readBytes(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Clip asset not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
