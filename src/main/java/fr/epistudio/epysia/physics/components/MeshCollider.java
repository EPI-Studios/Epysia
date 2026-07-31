package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.epymesh.BakedCollider;
import fr.epistudio.epysia.assets.epymesh.EpyMesh;
import fr.epistudio.epysia.assets.epymesh.EpyMeshFormat;
import fr.epistudio.epysia.assets.epymesh.EpyMeshSource;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.render.mesh.MeshData;

import java.util.Optional;

@EpysiaComponent(name = "Mesh Collider", category = "Physics")
public final class MeshCollider extends Collider {

    @Export(label = "Mesh")
    private String mesh = "";

    @Export(label = "Convex")
    private boolean convex = false;

    private Optional<MeshData> resolvedMesh = Optional.empty();
    private Optional<BakedCollider> bakedCollider = Optional.empty();
    private boolean meshDataProvided;
    private boolean geometryChanged;

    public boolean convex() {
        return convex;
    }

    public MeshCollider setMeshData(MeshData value) {
        resolvedMesh = Optional.of(value);
        bakedCollider = Optional.empty();
        meshDataProvided = true;
        geometryChanged = true;
        return this;
    }

    @Override
    public void markRegistered() {
        super.markRegistered();
        geometryChanged = false;
    }

    @Override
    public boolean requiresRebuild() {
        return isRegistered() && geometryChanged;
    }

    @Override
    public void onLoad(EngineServices services) {
        resolveMaterial(services);
        if (meshDataProvided) {
            return;
        }
        resolveCollisionGeometry();
    }

    @Override
    public ShapeDescriptor shape() {
        return bakedCollider
                .filter(this::bakedMatchesMode)
                .map(this::bakedShape)
                .orElseGet(this::cookedShape);
    }

    private boolean bakedMatchesMode(BakedCollider baked) {
        return convex ? baked.hasConvex() : baked.hasTriangleMesh();
    }

    private ShapeDescriptor bakedShape(BakedCollider baked) {
        return convex
                ? new ShapeDescriptor.ConvexHull(baked.convexVertices())
                : new ShapeDescriptor.TriangleMesh(baked.triangleVertices(), baked.triangleIndices());
    }

    private ShapeDescriptor cookedShape() {
        MeshData data = resolvedMesh.orElseThrow(() ->
                new EpysiaException("MeshCollider mesh not resolved; load the scene before registering physics."));
        return convex
                ? new ShapeDescriptor.ConvexHull(data.positions())
                : new ShapeDescriptor.TriangleMesh(data.positions(), data.indices());
    }

    private void resolveCollisionGeometry() {
        String path = resolveMeshPath();
        if (path.isEmpty()) {
            throw new EpysiaException("MeshCollider on '" + ownerName()
                    + "' has no mesh and no sibling MeshRenderer mesh to inherit.");
        }
        if (path.endsWith(EpyMeshFormat.EXTENSION)) {
            EpyMesh decoded = EpyMeshSource.load(path);
            resolvedMesh = Optional.of(decoded.mesh());
            bakedCollider = decoded.collider();
            return;
        }
        resolvedMesh = Optional.of(MeshDataSource.load(path));
        bakedCollider = Optional.empty();
    }

    private String resolveMeshPath() {
        if (!mesh.isEmpty()) {
            return mesh;
        }
        return owner()
                .flatMap(gameObject -> gameObject.getComponent(MeshRenderer.class))
                .map(renderer -> renderer.meshRef().path())
                .orElse("");
    }

    private String ownerName() {
        return owner().map(gameObject -> gameObject.name()).orElse("<detached>");
    }
}
