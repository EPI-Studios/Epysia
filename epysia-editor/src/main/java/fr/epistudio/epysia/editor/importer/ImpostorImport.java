package fr.epistudio.epysia.editor.importer;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.assets.loaders.ImpostorImportSettings;
import fr.epistudio.epysia.render.baking.ImpostorBakeRequest;
import fr.epistudio.epysia.render.baking.ImpostorBaker;
import fr.epistudio.epysia.render.baking.ImpostorPart;
import fr.epistudio.epysia.render.baking.ImpostorSurface;
import fr.epistudio.epysia.render.material.LitMaterial;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ImpostorImport {

    private static final String ALBEDO_FIELD = "albedo";

    private ImpostorImport() {
    }

    static List<Path> bakeWholeModel(GltfModel model, List<ImportedMesh> meshes,
                                     Map<MaterialModel, LitMaterial> materialsByModel, Path source,
                                     Path outputDirectory, Optional<ImpostorBaker> baker, List<String> warnings) {
        if (baker.isEmpty()) {
            return List.of();
        }
        List<ImpostorPart> parts = collectParts(model, meshes, materialsByModel, source, outputDirectory);
        if (parts.isEmpty()) {
            return List.of();
        }
        ImpostorImportSettings settings = ImpostorImportSettings.from(
                AssetMetaFile.settingsOf(source), AssetVariant.none());
        if (settings.exceedsAutomaticPartLimit(parts.size())) {
            warnings.add("Skipped the automatic impostor bake: " + parts.size()
                    + " parts is above the limit of " + ImpostorImportSettings.AUTOMATIC_PART_LIMIT
                    + ". This asset is a scene rather than a single object; set impostor: on in its"
                    + " .epymeta to bake it anyway.");
            return List.of();
        }
        return bake(baker.get(), new ImpostorBakeRequest(GltfImporter.fileStem(source), parts,
                outputDirectory, settings), warnings);
    }

    private static List<Path> bake(ImpostorBaker baker, ImpostorBakeRequest request, List<String> warnings) {
        try {
            return baker.bake(request);
        } catch (RuntimeException failure) {
            warnings.add("Impostor bake failed for " + request.name() + ": " + failure.getMessage());
            return List.of();
        }
    }

    private static List<ImpostorPart> collectParts(GltfModel model, List<ImportedMesh> meshes,
                                                   Map<MaterialModel, LitMaterial> materialsByModel, Path source,
                                                   Path outputDirectory) {
        Map<MeshModel, ImportedMesh> importedByModel = indexByMeshModel(meshes);
        List<ImpostorPart> parts = new ArrayList<>();
        for (NodeModel node : model.getNodeModels()) {
            for (MeshModel meshModel : node.getMeshModels()) {
                ImportedMesh imported = importedByModel.get(meshModel);
                if (imported != null) {
                    parts.add(partOf(imported, node, materialsByModel, source, outputDirectory));
                }
            }
        }
        return parts;
    }

    private static Map<MeshModel, ImportedMesh> indexByMeshModel(List<ImportedMesh> meshes) {
        Map<MeshModel, ImportedMesh> importedByModel = new IdentityHashMap<>();
        for (ImportedMesh mesh : meshes) {
            importedByModel.put(mesh.model(), mesh);
        }
        return importedByModel;
    }

    private static ImpostorPart partOf(ImportedMesh imported, NodeModel node,
                                       Map<MaterialModel, LitMaterial> materialsByModel, Path source,
                                       Path outputDirectory) {
        Matrix4f transform = new Matrix4f().set(node.computeGlobalTransform(null));
        return new ImpostorPart(imported.data(),
                surfacesFor(imported.model(), materialsByModel, source, outputDirectory), transform);
    }

    private static List<ImpostorSurface> surfacesFor(MeshModel meshModel,
                                                     Map<MaterialModel, LitMaterial> materialsByModel, Path source,
                                                     Path outputDirectory) {
        List<ImpostorSurface> surfaces = new ArrayList<>();
        for (MeshPrimitiveModel primitive : meshModel.getMeshPrimitiveModels()) {
            if (!GltfImporter.isTriangleMode(primitive.getMode())) {
                continue;
            }
            LitMaterial material = materialsByModel.get(primitive.getMaterialModel());
            surfaces.add(material == null
                    ? ImpostorSurface.untextured() : surfaceOf(material, source, outputDirectory));
        }
        return surfaces;
    }

    private static ImpostorSurface surfaceOf(LitMaterial material, Path source, Path outputDirectory) {
        Optional<Path> albedoImage = material.texturePath(ALBEDO_FIELD)
                .flatMap(path -> resolveImage(path, source, outputDirectory));
        boolean opaque = !material.transparent() && material.alphaCutoff <= 0.0f;
        Vector4f baseColor = new Vector4f(material.baseColor, 1.0f);
        return new ImpostorSurface(albedoImage, baseColor, material.alphaCutoff, opaque);
    }

    private static Optional<Path> resolveImage(String relativePath, Path source, Path outputDirectory) {
        Path beside = outputDirectory.resolve(relativePath);
        if (Files.isRegularFile(beside)) {
            return Optional.of(beside);
        }
        Path nextToSource = source.getParent().resolve(relativePath);
        return Files.isRegularFile(nextToSource) ? Optional.of(nextToSource) : Optional.empty();
    }
}
