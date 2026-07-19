package fr.epistudio.epysia.assets.source;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ClasspathAssetSource implements AssetSource {

    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");

    private final String logicalPath;

    public ClasspathAssetSource(String logicalPath) {
        this.logicalPath = logicalPath;
    }

    @Override
    public String path() {
        return logicalPath;
    }

    @Override
    public Optional<InputStream> open() {
        Path onDisk = RESOURCE_ROOT.resolve(logicalPath);
        if (Files.isRegularFile(onDisk)) {
            return Optional.of(openFile(onDisk));
        }
        return Optional.ofNullable(getClass().getClassLoader().getResourceAsStream(logicalPath));
    }

    private static InputStream openFile(Path path) {
        try {
            return Files.newInputStream(path);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to open " + path + ": " + exception.getMessage());
        }
    }
}
