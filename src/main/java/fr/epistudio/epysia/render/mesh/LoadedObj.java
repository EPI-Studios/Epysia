package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.material.Material;

import java.util.List;

public record LoadedObj(UploadedMesh mesh, List<Material> materials) {

    public LoadedObj {
        materials = List.copyOf(materials);
    }

    public void destroy(RenderBackend backend) {
        mesh.destroy(backend);
    }
}
