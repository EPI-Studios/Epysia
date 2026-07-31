package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyinstances.InstanceTransforms;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Multi Mesh Renderer", category = "Rendering")
public final class MultiMeshRenderer extends Component implements MeshRenderSource {

    private static final int MATRIX_FLOAT_COUNT = 16;
    public static final int TRANSFORM_FLOAT_COUNT = MATRIX_FLOAT_COUNT * 2;

    @Export(label = "Mesh")
    private final AssetRef<UploadedMesh> mesh = new AssetRef<>(UploadedMesh.class);
    @Export(label = "Material")
    private final AssetRef<Material> material = new AssetRef<>(Material.class);
    @Export(label = "Layer Mask", layerMask = true)
    private int layerMask = RenderLayers.DEFAULT;
    @Export(label = "Cast Shadows")
    private boolean castShadows = true;
    @Export(label = "Visible From", min = 0.0f, max = 100000.0f, step = 0.5f)
    private float visibilityRangeBegin;
    @Export(label = "Visible Until", min = 0.0f, max = 100000.0f, step = 0.5f)
    private float visibilityRangeEnd;

    @Export(label = "Instances")
    private final AssetRef<InstanceTransforms> instances = new AssetRef<>(InstanceTransforms.class);

    @Export(label = "Visible Instances", min = -1.0f, max = 1000000.0f, step = 1.0f)
    private int visibleInstanceCount = -1;

    private float[] instanceData = new float[0];
    private int instanceCount;
    private long dataRevision = 1L;
    private final Matrix4f scratchNormalMatrix = new Matrix4f();
    private final LevelOfDetailChain levelsOfDetail = new LevelOfDetailChain();

    public AssetRef<UploadedMesh> meshRef() {
        return mesh;
    }

    public MultiMeshRenderer addLevelOfDetail(UploadedMesh levelMesh, float switchDistance) {
        levelsOfDetail.addDirect(levelMesh, switchDistance);
        return this;
    }

    public MultiMeshRenderer addLevelOfDetailPath(String path, float switchDistance) {
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

    public AssetRef<Material> materialRef() {
        return material;
    }

    public AssetRef<InstanceTransforms> instancesRef() {
        return instances;
    }

    public MultiMeshRenderer setMesh(UploadedMesh value) {
        mesh.setDirect(value);
        return this;
    }

    public MultiMeshRenderer setMaterial(Material value) {
        material.setDirect(value);
        return this;
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

    public MultiMeshRenderer setVisibilityRange(float begin, float end) {
        this.visibilityRangeBegin = begin;
        this.visibilityRangeEnd = end;
        return this;
    }

    public MultiMeshRenderer setCastShadows(boolean value) {
        this.castShadows = value;
        return this;
    }

    @Override
    public Optional<Material> materialForSlot(int slot) {
        return material.direct();
    }

    public MultiMeshRenderer setLayerMask(int layerMask) {
        this.layerMask = layerMask;
        return this;
    }

    public MultiMeshRenderer setInstances(List<Matrix4f> transforms) {
        writeInstances(transforms);
        return this;
    }

    private void writeInstances(List<Matrix4f> transforms) {
        instanceData = new float[transforms.size() * TRANSFORM_FLOAT_COUNT];
        float[] scratch = new float[MATRIX_FLOAT_COUNT];
        for (int index = 0; index < transforms.size(); index++) {
            writeInstance(transforms.get(index), scratch, index);
        }
        instanceCount = transforms.size();
        dataRevision++;
    }

    private void writeInstance(Matrix4f model, float[] scratch, int index) {
        int base = index * TRANSFORM_FLOAT_COUNT;
        model.get(scratch);
        System.arraycopy(scratch, 0, instanceData, base, MATRIX_FLOAT_COUNT);
        model.normal(scratchNormalMatrix).get(scratch);
        System.arraycopy(scratch, 0, instanceData, base + MATRIX_FLOAT_COUNT, MATRIX_FLOAT_COUNT);
    }

    @Override
    public void onLoad(EngineServices services) {
        mesh.resolve(services.assets());
        material.resolve(services.assets());
        levelsOfDetail.resolve(services.assets());
        loadInstances(services);
        if (material.directOrNull() == null && mesh.directOrNull() != null) {
            attachDefaultMaterial(services);
        }
    }

    private void loadInstances(EngineServices services) {
        if (instances.isEmpty()) {
            return;
        }
        try {
            instances.resolve(services.assets()).ifPresent(this::setInstanceModels);
        } catch (RuntimeException error) {
            services.logger().error("[MultiMeshRenderer] Instances unavailable: " + instances.path(), error);
        }
    }

    public MultiMeshRenderer setInstanceModels(InstanceTransforms transforms) {
        float[] models = transforms.models();
        int count = transforms.count();
        instanceData = new float[count * TRANSFORM_FLOAT_COUNT];
        Matrix4f model = new Matrix4f();
        float[] scratch = new float[MATRIX_FLOAT_COUNT];
        for (int index = 0; index < count; index++) {
            System.arraycopy(models, index * MATRIX_FLOAT_COUNT, scratch, 0, MATRIX_FLOAT_COUNT);
            writeInstance(model.set(scratch), scratch, index);
        }
        instanceCount = count;
        dataRevision++;
        return this;
    }

    public InstanceTransforms instanceModels() {
        float[] models = new float[instanceCount * MATRIX_FLOAT_COUNT];
        for (int index = 0; index < instanceCount; index++) {
            System.arraycopy(instanceData, index * TRANSFORM_FLOAT_COUNT,
                    models, index * MATRIX_FLOAT_COUNT, MATRIX_FLOAT_COUNT);
        }
        return new InstanceTransforms(models);
    }

    private void attachDefaultMaterial(EngineServices services) {
        try {
            material.setDirect(new fr.epistudio.epysia.render.material.LitMaterial()
                    .setAlbedo(fr.epistudio.epysia.render.texture.Texture2D
                            .whitePixel(services.renderBackend()))
                    .setBaseColor(0.85f, 0.85f, 0.95f));
        } catch (RuntimeException error) {
            services.logger().error("[MultiMeshRenderer] Failed to attach default material", error);
        }
    }

    public Optional<UploadedMesh> mesh() {
        return mesh.direct();
    }

    public Optional<Material> material() {
        return material.direct();
    }

    @Override
    public UploadedMesh meshOrNull() {
        return mesh.directOrNull();
    }

    public Material materialOrNull() {
        return material.directOrNull();
    }

    public int instanceCount() {
        return instanceCount;
    }

    public int visibleInstanceCount() {
        return visibleInstanceCount < 0 ? instanceCount : Math.min(visibleInstanceCount, instanceCount);
    }

    public MultiMeshRenderer setVisibleInstanceCount(int value) {
        visibleInstanceCount = value;
        return this;
    }

    public float[] instanceData() {
        return instanceData;
    }

    public long dataRevision() {
        return dataRevision;
    }
}
