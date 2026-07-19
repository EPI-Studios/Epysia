package fr.epistudio.epysia.assets.source;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class FilesystemAssetSource implements AssetSource {

    private final Path file;

    public FilesystemAssetSource(Path file) {
        this.file = file;
    }

    @Override
    public String path() {
        return file.toString();
    }

    @Override
    public Optional<InputStream> open() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(file));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to open " + file + ": " + exception.getMessage());
        }
    }
}
