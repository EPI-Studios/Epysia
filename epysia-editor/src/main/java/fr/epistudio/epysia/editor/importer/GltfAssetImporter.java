package fr.epistudio.epysia.editor.importer;

import fr.epistudio.epysia.reflection.ComponentRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GltfAssetImporter implements AssetImporter {

    private static final String GLTF_EXTENSION = ".gltf";
    private static final String GLB_EXTENSION = ".glb";
    private static final String PREFAB_EXTENSION = ".epyprefab";
    private static final int IMPORTER_VERSION = 8;

    private final ComponentRegistry componentRegistry;

    public GltfAssetImporter(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }

    @Override
    public String displayName() {
        return "glTF";
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(GLTF_EXTENSION, GLB_EXTENSION);
    }

    @Override
    public Path primaryOutput(Path source, Path outputDirectory) {
        return outputDirectory.resolve(stem(source) + PREFAB_EXTENSION);
    }

    @Override
    public ImportOutcome importSource(Path source, Path outputDirectory) {
        GltfImportResult result = GltfImporter.importFile(source, outputDirectory, componentRegistry);
        return new ImportOutcome(collectOutputs(result), result.prefabFile(), result.warnings());
    }

    @Override
    public int version() {
        return IMPORTER_VERSION;
    }

    private static List<Path> collectOutputs(GltfImportResult result) {
        List<Path> outputs = new ArrayList<>();
        outputs.addAll(result.meshFiles());
        outputs.addAll(result.clipFiles());
        outputs.addAll(result.materialFiles());
        result.prefabFile().ifPresent(outputs::add);
        return outputs;
    }

    private static String stem(Path source) {
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
