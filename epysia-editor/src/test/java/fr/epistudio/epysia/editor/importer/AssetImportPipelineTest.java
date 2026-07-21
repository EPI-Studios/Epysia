package fr.epistudio.epysia.editor.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetImportPipelineTest {

    private static final String SOURCE_EXTENSION = ".fake";

    @Test
    void staleSourceIsImported(@TempDir Path directory) throws IOException {
        FakeImporter importer = new FakeImporter();
        AssetImportPipeline pipeline = pipelineWith(importer);
        Path source = writeSource(directory);
        assertTrue(pipeline.needsImport(source));
        Optional<ImportOutcome> outcome = pipeline.ensureImported(source);
        assertTrue(outcome.isPresent());
        assertEquals(1, importer.importCount);
        assertTrue(Files.exists(importer.primaryOutput(source, outputDirectoryFor(source))));
    }

    @Test
    void freshOutputIsNotReimported(@TempDir Path directory) throws IOException {
        FakeImporter importer = new FakeImporter();
        AssetImportPipeline pipeline = pipelineWith(importer);
        Path source = writeSource(directory);
        pipeline.ensureImported(source);
        makeOutputNewerThanSource(source, importer.primaryOutput(source, outputDirectoryFor(source)));
        assertFalse(pipeline.needsImport(source));
        Optional<ImportOutcome> outcome = pipeline.ensureImported(source);
        assertTrue(outcome.isPresent());
        assertTrue(outcome.get().instantiable().isPresent());
        assertEquals(1, importer.importCount);
    }

    @Test
    void failedSourceIsNotRetriedWithinSession(@TempDir Path directory) throws IOException {
        FakeImporter importer = new FakeImporter();
        importer.fail = true;
        AssetImportPipeline pipeline = pipelineWith(importer);
        Path source = writeSource(directory);
        assertTrue(pipeline.ensureImported(source).isEmpty());
        assertEquals(1, importer.importCount);
        assertFalse(pipeline.needsImport(source));
        assertTrue(pipeline.ensureImported(source).isEmpty());
        assertEquals(1, importer.importCount);
    }

    @Test
    void importWithoutOutputSettlesUntilSourceChanges(@TempDir Path directory) throws IOException {
        FakeImporter importer = new FakeImporter();
        importer.produceOutput = false;
        AssetImportPipeline pipeline = pipelineWith(importer);
        Path source = writeSource(directory);
        assertTrue(pipeline.ensureImported(source).isPresent());
        assertEquals(1, importer.importCount);
        assertFalse(pipeline.needsImport(source));
        Files.setLastModifiedTime(source, FileTime.fromMillis(9_000_000L));
        assertTrue(pipeline.needsImport(source));
    }

    @Test
    void importerVersionBumpTriggersReimport(@TempDir Path directory) throws IOException {
        FakeImporter importer = new FakeImporter();
        AssetImportPipeline pipeline = pipelineWith(importer);
        Path source = writeSource(directory);
        pipeline.ensureImported(source);
        Path output = importer.primaryOutput(source, outputDirectoryFor(source));
        makeOutputNewerThanSource(source, output);
        assertFalse(pipeline.needsImport(source));
        importer.version = 2;
        assertTrue(pipeline.needsImport(source));
        Optional<ImportOutcome> outcome = pipeline.ensureImported(source);
        assertTrue(outcome.isPresent());
        assertEquals(2, importer.importCount);
        assertEquals("2", Files.readString(versionSidecarFor(output)));
        assertFalse(pipeline.needsImport(source));
    }

    private static Path versionSidecarFor(Path output) {
        return output.resolveSibling(output.getFileName().toString() + ".importversion");
    }

    private static AssetImportPipeline pipelineWith(AssetImporter importer) {
        AssetImporterRegistry registry = new AssetImporterRegistry();
        registry.register(importer);
        return new AssetImportPipeline(registry);
    }

    private static Path writeSource(Path directory) throws IOException {
        Path source = directory.resolve("model" + SOURCE_EXTENSION);
        Files.writeString(source, "source");
        return source;
    }

    private static Path outputDirectoryFor(Path source) {
        return source.getParent().resolve("model");
    }

    private static void makeOutputNewerThanSource(Path source, Path output) throws IOException {
        Files.setLastModifiedTime(source, FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(output, FileTime.fromMillis(5_000L));
    }

    private static final class FakeImporter implements AssetImporter {

        private int importCount;
        private boolean fail;
        private boolean produceOutput = true;
        private int version = 1;

        @Override
        public String displayName() {
            return "Fake";
        }

        @Override
        public Set<String> supportedExtensions() {
            return Set.of(SOURCE_EXTENSION);
        }

        @Override
        public Path primaryOutput(Path source, Path outputDirectory) {
            return outputDirectory.resolve("model.prefab");
        }

        @Override
        public ImportOutcome importSource(Path source, Path outputDirectory) {
            importCount++;
            if (fail) {
                throw new IllegalStateException("import failed");
            }
            Path output = primaryOutput(source, outputDirectory);
            if (!produceOutput) {
                return new ImportOutcome(List.of(), Optional.empty(), List.of());
            }
            writeOutput(output);
            return new ImportOutcome(List.of(output), Optional.of(output), List.of());
        }

        @Override
        public int version() {
            return version;
        }

        private static void writeOutput(Path output) {
            try {
                Files.writeString(output, "output");
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }
}
