package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@EpysiaComponent(name = "Density Volume", category = "Rendering")
@RequiresComponent(Transform3D.class)
public final class DensityVolume extends Component {
    public static final int MAXIMUM_VOXELS = 4_194_304;

    @Export(label = "Extents", min = 0.5f, max = 200.0f, step = 0.1f)
    private final Vector3f extents = new Vector3f(3.0f, 3.0f, 3.0f);

    @Export(label = "Voxel Size", min = 0.05f, max = 4.0f, step = 0.01f)
    private float voxelSize = 0.5f;

    @Export(label = "Occupancy From Colliders")
    private boolean occupancyFromColliders = true;

    @Export(label = "Occupancy Layers", layerMask = true)
    private int occupancyLayers = -1;

    private boolean occupancyDirty = true;

    public Vector3fc extents() {
        return extents;
    }

    public DensityVolume setExtents(float x, float y, float z) {
        extents.set(Math.max(0.01f, x), Math.max(0.01f, y), Math.max(0.01f, z));
        occupancyDirty = true;
        return this;
    }

    public float voxelSize() {
        return voxelSize;
    }

    public DensityVolume setVoxelSize(float size) {
        voxelSize = Math.max(0.01f, size);
        occupancyDirty = true;
        return this;
    }

    public boolean occupancyFromColliders() {
        return occupancyFromColliders;
    }

    public DensityVolume setOccupancyFromColliders(boolean enabled) {
        occupancyFromColliders = enabled;
        occupancyDirty = true;
        return this;
    }

    public int occupancyLayers() {
        return occupancyLayers;
    }

    public int voxelsAlong(float extent) {
        return Math.max(1, (int) Math.ceil(extent * 2.0f / voxelSize));
    }

    public int resolutionX() {
        return voxelsAlong(extents.x);
    }

    public int resolutionY() {
        return voxelsAlong(extents.y);
    }

    public int resolutionZ() {
        return voxelsAlong(extents.z);
    }

    public int voxelCount() {
        return resolutionX() * resolutionY() * resolutionZ();
    }

    public boolean withinVoxelBudget() {
        return (long) resolutionX() * resolutionY() * resolutionZ() <= MAXIMUM_VOXELS;
    }

    public boolean occupancyDirty() {
        return occupancyDirty;
    }

    public void markOccupancyDirty() {
        occupancyDirty = true;
    }

    public void clearOccupancyDirty() {
        occupancyDirty = false;
    }

    public Matrix4f worldMatrix(Matrix4f destination, float interpolationAlpha) {
        return owner()
                .flatMap(gameObject -> gameObject.getComponent(Transform3D.class))
                .map(transform -> destination.set(transform.worldMatrix(interpolationAlpha)))
                .orElseGet(destination::identity);
    }
}
