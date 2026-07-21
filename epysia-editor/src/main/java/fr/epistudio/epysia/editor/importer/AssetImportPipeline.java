package fr.epistudio.epysia.editor.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AssetImportPipeline {

    private final AssetImporterRegistry registry;
    private final Set<Path> failedSources = new HashSet<>();

    public AssetImportPipeline(AssetImporterRegistry registry) {
        this.registry = registry;
    }

    public Optional<AssetImporter> importerFor(Path source) {
        return registry.forPath(source);
    }

    public boolean needsImport(Path source) {
        Optional<AssetImporter> importer = registry.forPath(source);
        if (importer.isEmpty() || failedSources.contains(source)) {
            return false;
        }
        return isStale(importer.get(), source);
    }

    public Optional<ImportOutcome> ensureImported(Path source) {
        Optional<AssetImporter> importer = registry.forPath(source);
        if (importer.isEmpty() || failedSources.contains(source)) {
            return Optional.empty();
        }
        if (isStale(importer.get(), source)) {
            return runImport(importer.get(), source);
        }
        return Optional.of(upToDateOutcome(importer.get(), source));
    }

    public Optional<ImportOutcome> reimport(Path source) {
        Optional<AssetImporter> importer = registry.forPath(source);
        if (importer.isEmpty()) {
            return Optional.empty();
        }
        failedSources.remove(source);
        return runImport(importer.get(), source);
    }

    private boolean isStale(AssetImporter importer, Path source) {
        Path primaryOutput = importer.primaryOutput(source, outputDirectoryFor(source));
        if (!Files.exists(primaryOutput)) {
            return true;
        }
        return sourceNewerThan(source, primaryOutput);
    }

    private static boolean sourceNewerThan(Path source, Path output) {
        try {
            return Files.getLastModifiedTime(source).toMillis() > Files.getLastModifiedTime(output).toMillis();
        } catch (IOException unreadable) {
            return true;
        }
    }

    private Optional<ImportOutcome> runImport(AssetImporter importer, Path source) {
        try {
            Path outputDirectory = outputDirectoryFor(source);
            Files.createDirectories(outputDirectory);
            return Optional.of(importer.importSource(source, outputDirectory));
        } catch (RuntimeException | IOException error) {
            failedSources.add(source);
            return Optional.empty();
        }
    }

    private ImportOutcome upToDateOutcome(AssetImporter importer, Path source) {
        Path primaryOutput = importer.primaryOutput(source, outputDirectoryFor(source));
        Optional<Path> instantiable = Files.exists(primaryOutput) ? Optional.of(primaryOutput) : Optional.empty();
        return new ImportOutcome(List.of(), instantiable, List.of());
    }

    private static Path outputDirectoryFor(Path source) {
        return source.getParent().resolve(stem(source));
    }

    private static String stem(Path source) {
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
