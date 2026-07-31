package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.assets.loaders.ImpostorImportSettings;
import fr.epistudio.epysia.render.mesh.MeshData;

import java.nio.file.Path;
import java.util.List;

public record ImpostorBakeRequest(String name, List<ImpostorPart> parts, Path outputDirectory,
                                  ImpostorImportSettings settings) {

    public ImpostorBakeRequest {
        parts = List.copyOf(parts);
    }

    public static ImpostorBakeRequest singleMesh(String name, MeshData mesh, List<ImpostorSurface> surfaces,
                                                 Path outputDirectory, ImpostorImportSettings settings) {
        return new ImpostorBakeRequest(name, List.of(ImpostorPart.untransformed(mesh, surfaces)),
                outputDirectory, settings);
    }

    public int triangleCount() {
        int triangles = 0;
        for (ImpostorPart part : parts) {
            triangles += part.triangleCount();
        }
        return triangles;
    }

    public boolean hasSkinnedPart() {
        for (ImpostorPart part : parts) {
            if (part.mesh().hasSkin()) {
                return true;
            }
        }
        return false;
    }
}
