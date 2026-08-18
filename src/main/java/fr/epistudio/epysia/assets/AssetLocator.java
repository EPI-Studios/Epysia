package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.assets.source.ClasspathAssetSource;
import fr.epistudio.epysia.assets.source.FilesystemAssetSource;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public final class AssetLocator {

    private final Optional<Path> projectRoot;

    private AssetLocator(Optional<Path> projectRoot) {
        this.projectRoot = projectRoot;
    }

    public static AssetLocator withoutProject() {
        return new AssetLocator(Optional.empty());
    }

    public static AssetLocator forProject(Path root) {
        return new AssetLocator(Optional.of(root.toAbsolutePath().normalize()));
    }

    public Optional<Path> projectRoot() {
        return projectRoot;
    }

    public Optional<AssetSource> open(AssetUri uri) {
        return switch (uri.scheme()) {
            case NONE -> Optional.empty();
            case ENGINE -> Optional.of(new ClasspathAssetSource(uri.path()));
            case PROJECT, SYSTEM -> file(uri).map(FilesystemAssetSource::new);
        };
    }

    public Optional<Path> file(AssetUri uri) {
        return switch (uri.scheme()) {
            case NONE, ENGINE -> Optional.empty();
            case SYSTEM -> asPath(uri.path());
            case PROJECT -> asPath(uri.path()).flatMap(relative -> projectRoot.map(root -> root.resolve(relative)));
        };
    }

    public Optional<Path> file(String reference) {
        Optional<Path> onDisk = asPath(reference).filter(Files::isRegularFile);
        return onDisk.isPresent() ? onDisk : AssetUri.parse(reference).flatMap(this::file);
    }

    private static Optional<Path> asPath(String text) {
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException unusableOnThisFilesystem) {
            return Optional.empty();
        }
    }

    public boolean exists(AssetUri uri) {
        return switch (uri.scheme()) {
            case NONE -> false;
            case ENGINE -> open(uri).flatMap(AssetSource::open).isPresent();
            case PROJECT, SYSTEM -> file(uri).filter(Files::isRegularFile).isPresent();
        };
    }

    public String resolvedPath(AssetUri uri) {
        return switch (uri.scheme()) {
            case NONE -> "";
            case ENGINE -> uri.path();
            case PROJECT, SYSTEM -> file(uri).map(Path::toString).orElse(uri.path());
        };
    }

    public AssetUri fromFile(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        return projectRoot
                .filter(absolute::startsWith)
                .map(root -> AssetUri.project(root.relativize(absolute).toString()))
                .orElseGet(() -> AssetUri.system(absolute));
    }
}
