package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.material.Material;

import java.util.List;

public record LoadedObj(UploadedMesh mesh, List<Material> materials, List<String> warnings) {

    public LoadedObj {
        materials = List.copyOf(materials);
        warnings = List.copyOf(warnings);
    }

    public LoadedObj(UploadedMesh mesh, List<Material> materials) {
        this(mesh, materials, List.of());
    }

    public void destroy(RenderBackend backend) {
        mesh.destroy(backend);
    }
}
