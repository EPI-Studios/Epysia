package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AcquiredAssets;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.AssetUri;
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
public final class MeshRenderer extends Component implements MeshRenderSource {

    @Export(label = "Mesh")
    private final AssetRef<UploadedMesh> mesh = new AssetRef<>(UploadedMesh.class);
    @Export(label = "Layer Mask", layerMask = true)
    private int layerMask = RenderLayers.DEFAULT;
    @Export(label = "Cast Shadows")
    private boolean castShadows = true;
    @Export(label = "View Model")
    private boolean viewModel;
    @Export(label = "Visible From", min = 0.0f, max = 100000.0f, step = 0.5f)
    private float visibilityRangeBegin;
    @Export(label = "Visible Until", min = 0.0f, max = 100000.0f, step = 0.5f)
    private float visibilityRangeEnd;
    private final List<Material> materials = new ArrayList<>();
    private final LevelOfDetailChain levelsOfDetail = new LevelOfDetailChain();
    private final AcquiredAssets ownedTextures = new AcquiredAssets();

    public AssetRef<UploadedMesh> meshRef() {
        return mesh;
    }

    public MeshRenderer addLevelOfDetail(UploadedMesh levelMesh, float switchDistance) {
        levelsOfDetail.addDirect(levelMesh, switchDistance);
        return this;
    }

    public MeshRenderer addLevelOfDetailPath(String path, float switchDistance) {
        levelsOfDetail.addPath(path, switchDistance);
        return this;
    }

    public int levelOfDetailCount() {
        return levelsOfDetail.count();
    }

    public int activeLevelOfDetail() {
        return levelsOfDetail.activeLevel();
    }

    public UploadedMesh meshForDistance(float distance) {
        return levelsOfDetail.meshForDistance(distance, mesh.directOrNull());
    }

    public boolean viewModel() {
        return viewModel;
    }

    public MeshRenderer setViewModel(boolean value) {
        viewModel = value;
        return this;
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

    @Override
    public int layerMask() {
        return layerMask;
    }

    @Override
    public boolean castsShadows() {
        return castShadows;
    }

    @Override
    public float visibilityRangeBegin() {
        return visibilityRangeBegin;
    }

    @Override
    public float visibilityRangeEnd() {
        return visibilityRangeEnd;
    }

    public MeshRenderer setVisibilityRange(float begin, float end) {
        this.visibilityRangeBegin = begin;
        this.visibilityRangeEnd = end;
        return this;
    }

    public MeshRenderer setCastShadows(boolean value) {
        this.castShadows = value;
        return this;
    }

    public MeshRenderer setLayerMask(int layerMask) {
        this.layerMask = layerMask;
        return this;
    }

    @Override
    public UploadedMesh meshOrNull() {
        return mesh.directOrNull();
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

    @Override
    public Optional<Material> materialForSlot(int slot) {
        if (slot < 0 || slot >= materials.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(materials.get(slot));
    }

    @Override
    public void copyStateFrom(IComponent source) {
        if (source instanceof MeshRenderer other) {
            setMaterials(other.materials());
        }
    }

    @Override
    public void onLoad(EngineServices services) {
        if (mesh.direct().isEmpty() && !mesh.isEmpty()) {
            resolveMeshAndMaterials(services);
        }
        if (mesh.direct().isPresent() && materials.isEmpty()) {
            attachDefaultMaterial(services);
        }
        levelsOfDetail.resolve(services.assets());
        resolveMaterialTextures(services);
    }

    private void resolveMaterialTextures(EngineServices services) {
        ownedTextures.releaseAll(services.assets());
        for (Material material : materials) {
            try {
                MaterialFields.acquireTextures(material, services.assets(), ownedTextures);
            } catch (RuntimeException error) {
                services.logger().error("[MeshRenderer] Texture resolution failed", error);
            }
        }
    }

    @Override
    public void onDestroy(EngineServices services) {
        super.onDestroy(services);
        ownedTextures.releaseAll(services.assets());
        releaseInlineMaterialTextures(services);
    }

    private void releaseInlineMaterialTextures(EngineServices services) {
        for (Material material : materials) {
            if (!material.isAssetBacked()) {
                MaterialFields.releaseTextures(material, services.assets());
            }
        }
    }

    private void resolveMeshAndMaterials(EngineServices services) {
        String path = mesh.path();
        if (path.endsWith(".obj")) {
            try {
                LoadedObj loaded = services.assets().resolveOrCompute(LoadedObj.class, path,
                        () -> loadObj(services, path));
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

    private static LoadedObj loadObj(EngineServices services, String path) {
        LoadedObj loaded = ObjLoader.load(services.renderBackend(), resolvedObjPath(services, path));
        loaded.warnings().forEach(warning -> services.logger().warn("[MeshRenderer] " + warning));
        return loaded;
    }

    private static String resolvedObjPath(EngineServices services, String path) {
        return AssetUri.parse(path)
                .filter(uri -> !uri.isEmpty())
                .map(uri -> services.assets().locator().resolvedPath(uri))
                .orElse(path);
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
