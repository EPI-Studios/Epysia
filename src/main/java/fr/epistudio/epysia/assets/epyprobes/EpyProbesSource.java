package fr.epistudio.epysia.assets.epyprobes;

import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class EpyProbesSource {

    private static final String CLASSPATH_ROOT = "probes/";

    private EpyProbesSource() {
    }

    public static BakedProbes load(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Probes not found on filesystem or classpath: " + path));
        return EpyProbesReader.read(readBytes(source));
    }

    private static byte[] readBytes(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Probes not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage(), exception);
        }
    }
}
