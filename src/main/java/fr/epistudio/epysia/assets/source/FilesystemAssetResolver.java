package fr.epistudio.epysia.assets.source;

import java.nio.file.Path;
import java.util.Optional;

public final class FilesystemAssetResolver implements AssetResolver {

    private final Path baseDirectory;

    public FilesystemAssetResolver(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public Optional<AssetSource> resolve(String name) {
        Path candidate = Path.of(name);
        Path target = candidate.isAbsolute() ? candidate : baseDirectory.resolve(candidate);
        return Optional.of(new FilesystemAssetSource(target));
    }

    @Override
    public AssetResolver relativeTo(String subDirectory) {
        return new FilesystemAssetResolver(baseDirectory.resolve(subDirectory));
    }
}
