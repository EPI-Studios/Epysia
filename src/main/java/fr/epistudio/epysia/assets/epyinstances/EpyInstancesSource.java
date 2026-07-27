package fr.epistudio.epysia.assets.epyinstances;

import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class EpyInstancesSource {

    private static final String CLASSPATH_ROOT = "instances/";

    private EpyInstancesSource() {
    }

    public static InstanceTransforms load(String path) {
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Instances not found on filesystem or classpath: " + path));
        return EpyInstancesReader.read(readBytes(source));
    }

    private static byte[] readBytes(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Instances not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage(), exception);
        }
    }
}
