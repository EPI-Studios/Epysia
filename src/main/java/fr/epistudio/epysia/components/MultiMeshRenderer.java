package fr.epistudio.epysia.components;

import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Multi Mesh Renderer", category = "Rendering")
public final class MultiMeshRenderer extends Component {

    private static final int MATRIX_FLOAT_COUNT = 16;

    private UploadedMesh mesh;
    private Material material;
    private float[] instanceData = new float[0];
    private int instanceCount;
    private boolean dirty;

    public MultiMeshRenderer setMesh(UploadedMesh value) {
        this.mesh = value;
        return this;
    }

    public MultiMeshRenderer setMaterial(Material value) {
        this.material = value;
        return this;
    }

    public MultiMeshRenderer setInstances(List<Matrix4f> transforms) {
        instanceData = new float[transforms.size() * MATRIX_FLOAT_COUNT];
        float[] scratch = new float[MATRIX_FLOAT_COUNT];
        for (int i = 0; i < transforms.size(); i++) {
            transforms.get(i).get(scratch);
            System.arraycopy(scratch, 0, instanceData, i * MATRIX_FLOAT_COUNT, MATRIX_FLOAT_COUNT);
        }
        instanceCount = transforms.size();
        dirty = true;
        return this;
    }

    public Optional<UploadedMesh> mesh() {
        return Optional.ofNullable(mesh);
    }

    public Optional<Material> material() {
        return Optional.ofNullable(material);
    }

    public int instanceCount() {
        return instanceCount;
    }

    public float[] instanceData() {
        return instanceData;
    }

    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
