package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Mesh Renderer", category = "Rendering")
@RequiresComponent(Transform3D.class)
public final class MeshRenderer extends Component {

    private UploadedMesh mesh;
    private final List<Material> materials = new ArrayList<>();

    public MeshRenderer setMesh(UploadedMesh mesh) {
        this.mesh = mesh;
        return this;
    }

    public MeshRenderer setMaterial(Material material) {
        materials.clear();
        materials.add(material);
        return this;
    }

    public MeshRenderer setMaterials(List<Material> materials) {
        this.materials.clear();
        this.materials.addAll(materials);
        return this;
    }

    public MeshRenderer addMaterial(Material material) {
        materials.add(material);
        return this;
    }

    public UploadedMesh mesh() {
        return mesh;
    }

    public Optional<Material> materialForSlot(int slot) {
        if (slot < 0 || slot >= materials.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(materials.get(slot));
    }
}
