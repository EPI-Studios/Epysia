package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.editor.importer.AssetImportPipeline;
import fr.epistudio.epysia.editor.importer.AssetImporterRegistry;
import fr.epistudio.epysia.editor.importer.GltfAssetImporter;
import fr.epistudio.epysia.editor.importer.ImportOutcome;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record ImportRun(List<Path> sources) {

    private static final String IMPORT_FLAG = "--import";
    private static final String LOG_PREFIX = "[import] ";

    public static Optional<ImportRun> parse(String[] arguments) {
        List<Path> sources = valuesOf(arguments, IMPORT_FLAG);
        if (sources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImportRun(sources));
    }

    public void run() throws IOException {
        AssetImportPipeline pipeline = new AssetImportPipeline(buildRegistry());
        for (Path source : expandSources()) {
            importOne(pipeline, source);
        }
    }

    private void importOne(AssetImportPipeline pipeline, Path source) {
        System.out.println(LOG_PREFIX + source);
        Optional<ImportOutcome> outcome = pipeline.reimport(source);
        if (outcome.isEmpty()) {
            throw new EpysiaException("Import failed for " + source);
        }
        report(outcome.get());
    }

    private static void report(ImportOutcome outcome) {
        for (Path output : outcome.outputs()) {
            System.out.println(LOG_PREFIX + "wrote " + output.getFileName());
        }
        for (String warning : outcome.warnings()) {
            System.out.println(LOG_PREFIX + "warning " + warning);
        }
        System.out.println(LOG_PREFIX + "produced " + outcome.outputs().size() + " files");
    }

    private List<Path> expandSources() throws IOException {
        List<Path> expanded = new ArrayList<>();
        for (Path source : sources) {
            if (Files.isDirectory(source)) {
                expanded.addAll(modelsUnder(source));
            } else {
                expanded.add(source);
            }
        }
        return expanded;
    }

    private static List<Path> modelsUnder(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(ImportRun::isModel)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static boolean isModel(Path candidate) {
        String name = candidate.getFileName().toString().toLowerCase();
        return name.endsWith(".glb") || name.endsWith(".gltf");
    }

    private static AssetImporterRegistry buildRegistry() {
        ComponentRegistry componentRegistry = new ComponentRegistry();
        componentRegistry.populateFromScan(ComponentScanner.scan());
        AssetImporterRegistry registry = new AssetImporterRegistry();
        registry.register(new GltfAssetImporter(componentRegistry));
        return registry;
    }

    private static List<Path> valuesOf(String[] arguments, String flag) {
        List<Path> values = new ArrayList<>();
        for (int index = 0; index < arguments.length - 1; index++) {
            if (flag.equals(arguments[index])) {
                values.add(Path.of(arguments[index + 1]));
            }
        }
        return values;
    }
}
