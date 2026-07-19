package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.render.mesh.LoadedObj;
import fr.epistudio.epysia.render.mesh.ObjLoader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.texture.Texture2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Mesh Renderer", category = "Rendering")
@RequiresComponent(Transform3D.class)
public final class MeshRenderer extends Component {

    @Export(label = "Mesh")
    private final AssetRef<UploadedMesh> mesh = new AssetRef<>(UploadedMesh.class);
    private final List<Material> materials = new ArrayList<>();

    public AssetRef<UploadedMesh> meshRef() {
        return mesh;
    }

    public MeshRenderer setMesh(UploadedMesh value) {
        mesh.setDirect(value);
        return this;
    }

    public MeshRenderer setMeshPath(String path) {
        mesh.setPath(path);
        return this;
    }

    public Optional<UploadedMesh> mesh() {
        return mesh.direct();
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

    public List<Material> materials() {
        return Collections.unmodifiableList(materials);
    }

    public Optional<Material> materialForSlot(int slot) {
        if (slot < 0 || slot >= materials.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(materials.get(slot));
    }

    @Override
    public void onLoad(EngineServices services) {
        if (mesh.direct().isEmpty() && !mesh.isEmpty()) {
            resolveMeshAndMaterials(services);
        }
        if (mesh.direct().isPresent() && materials.isEmpty()) {
            attachDefaultMaterial(services);
        }
        resolveMaterialTextures(services);
    }

    private void resolveMaterialTextures(EngineServices services) {
        for (Material material : materials) {
            try {
                MaterialFields.resolveTextures(material, services.assets());
            } catch (RuntimeException error) {
                services.logger().error("[MeshRenderer] Texture resolution failed", error);
            }
        }
    }

    private void resolveMeshAndMaterials(EngineServices services) {
        String path = mesh.path();
        if (path.endsWith(".obj")) {
            try {
                LoadedObj loaded = ObjLoader.load(services.renderBackend(), path);
                loaded.warnings().forEach(warning -> services.logger().warn("[MeshRenderer] " + warning));
                mesh.setDirect(loaded.mesh());
                if (!loaded.materials().isEmpty() && materials.size() != loaded.materials().size()) {
                    setMaterials(loaded.materials());
                }
                return;
            } catch (RuntimeException error) {
                services.logger().error("[MeshRenderer] OBJ load failed for " + path, error);
            }
        }
        mesh.resolve(services.assets());
    }

    private void attachDefaultMaterial(EngineServices services) {
        try {
            setMaterial(new LitMaterial()
                    .setAlbedo(Texture2D.whitePixel(services.renderBackend()))
                    .setBaseColor(0.85f, 0.85f, 0.95f));
        } catch (RuntimeException error) {
            services.logger().error("[MeshRenderer] Failed to attach default material", error);
        }
    }
}
