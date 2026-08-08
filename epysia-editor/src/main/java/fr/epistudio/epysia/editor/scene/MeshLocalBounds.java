package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.gizmo.SelectionHierarchy;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.mesh.Aabb;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public final class MeshLocalBounds {

    private MeshLocalBounds() {
    }

    public static Optional<Aabb> of(GameObject gameObject) {
        Optional<Matrix4f> toLocal = worldToLocal(gameObject);
        if (toLocal.isEmpty()) {
            return Optional.empty();
        }
        BoundsBuilder builder = new BoundsBuilder();
        for (GameObject meshObject : SelectionHierarchy.meshObjectsUnder(List.of(gameObject))) {
            accumulate(builder, meshObject, toLocal.get());
        }
        return builder.result();
    }

    private static Optional<Matrix4f> worldToLocal(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .map(transform -> new Matrix4f(transform.worldMatrix()).invert());
    }

    private static void accumulate(BoundsBuilder builder, GameObject meshObject, Matrix4f toLocal) {
        Optional<UploadedMesh> mesh = meshObject.getComponent(MeshRenderer.class)
                .flatMap(MeshRenderer::mesh);
        Optional<Transform3D> transform = meshObject.getComponent(Transform3D.class);
        if (mesh.isEmpty() || transform.isEmpty()) {
            return;
        }
        Matrix4f meshToLocal = new Matrix4f(toLocal).mul(transform.get().worldMatrix());
        accumulateCorners(builder, mesh.get().localBounds(), meshToLocal);
    }

    private static void accumulateCorners(BoundsBuilder builder, Aabb bounds, Matrix4f meshToLocal) {
        Vector3f corner = new Vector3f();
        for (int index = 0; index < 8; index++) {
            corner.set((index & 1) == 0 ? bounds.minX() : bounds.maxX(),
                    (index & 2) == 0 ? bounds.minY() : bounds.maxY(),
                    (index & 4) == 0 ? bounds.minZ() : bounds.maxZ());
            meshToLocal.transformPosition(corner);
            builder.expand(corner);
        }
    }

    private static final class BoundsBuilder {

        private final Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        private final Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        private boolean filled;

        void expand(Vector3f point) {
            minimum.min(point);
            maximum.max(point);
            filled = true;
        }

        Optional<Aabb> result() {
            if (!filled) {
                return Optional.empty();
            }
            return Optional.of(new Aabb(minimum.x, minimum.y, minimum.z, maximum.x, maximum.y, maximum.z));
        }
    }
}
