package fr.epistudio.epysia.editor.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AssetImportPipeline {

    private static final String VERSION_SIDECAR_SUFFIX = ".importversion";

    private final AssetImporterRegistry registry;
    private final Set<Path> failedSources = new HashSet<>();
    private final Map<Path, CompletedImport> completedWithoutOutput = new HashMap<>();

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
        if (settledWithoutOutput(importer.get(), source)) {
            return false;
        }
        return isStale(importer.get(), source);
    }

    private boolean settledWithoutOutput(AssetImporter importer, Path source) {
        CompletedImport settled = completedWithoutOutput.get(source);
        if (settled == null) {
            return false;
        }
        if (settled.importerVersion() != importer.version()) {
            completedWithoutOutput.remove(source);
            return false;
        }
        if (sourceModifiedMillis(source) == settled.sourceModifiedMillis()) {
            return true;
        }
        completedWithoutOutput.remove(source);
        return false;
    }

    private record CompletedImport(long sourceModifiedMillis, int importerVersion) {
    }

    private static long sourceModifiedMillis(Path source) {
        try {
            return Files.getLastModifiedTime(source).toMillis();
        } catch (IOException unreadable) {
            return -1L;
        }
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
        completedWithoutOutput.remove(source);
        return runImport(importer.get(), source);
    }

    private boolean isStale(AssetImporter importer, Path source) {
        Path primaryOutput = importer.primaryOutput(source, outputDirectoryFor(source));
        if (!Files.exists(primaryOutput)) {
            return true;
        }
        if (isVersionStale(importer, primaryOutput)) {
            return true;
        }
        return sourceNewerThan(source, primaryOutput);
    }

    private static boolean isVersionStale(AssetImporter importer, Path primaryOutput) {
        Optional<Integer> recordedVersion = readVersionSidecar(primaryOutput);
        return recordedVersion.isEmpty() || recordedVersion.get() != importer.version();
    }

    private static Path versionSidecarFor(Path primaryOutput) {
        return primaryOutput.resolveSibling(primaryOutput.getFileName().toString() + VERSION_SIDECAR_SUFFIX);
    }

    private static Optional<Integer> readVersionSidecar(Path primaryOutput) {
        Path sidecar = versionSidecarFor(primaryOutput);
        if (!Files.exists(sidecar)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(Files.readString(sidecar).trim()));
        } catch (IOException | NumberFormatException unreadable) {
            return Optional.empty();
        }
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
            ImportOutcome outcome = importer.importSource(source, outputDirectory);
            writeVersionSidecarIfOutputPresent(importer, source);
            recordSettlementIfOutputMissing(importer, source);
            return Optional.of(outcome);
        } catch (RuntimeException | IOException error) {
            failedSources.add(source);
            return Optional.empty();
        }
    }

    private void writeVersionSidecarIfOutputPresent(AssetImporter importer, Path source) throws IOException {
        Path primaryOutput = importer.primaryOutput(source, outputDirectoryFor(source));
        if (Files.exists(primaryOutput)) {
            Files.writeString(versionSidecarFor(primaryOutput), Integer.toString(importer.version()));
        }
    }

    private void recordSettlementIfOutputMissing(AssetImporter importer, Path source) {
        Path primaryOutput = importer.primaryOutput(source, outputDirectoryFor(source));
        if (!Files.exists(primaryOutput)) {
            completedWithoutOutput.put(source, new CompletedImport(sourceModifiedMillis(source), importer.version()));
        } else {
            completedWithoutOutput.remove(source);
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
