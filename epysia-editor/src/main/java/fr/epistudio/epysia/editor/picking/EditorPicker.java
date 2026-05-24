package fr.epistudio.epysia.editor.picking;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.mesh.Aabb;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

public final class EditorPicker {

    private static final float NO_HIT = Float.POSITIVE_INFINITY;
    private static final float NON_MESH_PICK_RADIUS = 0.35f;
    private static final Aabb NON_MESH_BOUNDS = new Aabb(
            -NON_MESH_PICK_RADIUS, -NON_MESH_PICK_RADIUS, -NON_MESH_PICK_RADIUS,
            NON_MESH_PICK_RADIUS, NON_MESH_PICK_RADIUS, NON_MESH_PICK_RADIUS);

    private final Matrix4f inverseViewProjection = new Matrix4f();
    private final Matrix4f inverseModel = new Matrix4f();
    private final Vector4f nearPointClip = new Vector4f();
    private final Vector4f farPointClip = new Vector4f();
    private final Vector3f rayOrigin = new Vector3f();
    private final Vector3f rayDirection = new Vector3f();
    private final Vector3f localOrigin = new Vector3f();
    private final Vector3f localDirection = new Vector3f();

    public int pickAt(int cursorX, int cursorY,
                      int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                      Matrix4f viewProjection, List<GameObject> candidates) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || candidates.isEmpty()) {
            return -1;
        }
        computeWorldRay(cursorX, cursorY, viewportX, viewportY, viewportWidth, viewportHeight, viewProjection);
        return findNearestHit(candidates);
    }

    private void computeWorldRay(int cursorX, int cursorY, int viewportX, int viewportY,
                                 int viewportWidth, int viewportHeight, Matrix4f viewProjection) {
        float ndcX = (2.0f * (cursorX - viewportX) / viewportWidth) - 1.0f;
        float ndcY = 1.0f - (2.0f * (cursorY - viewportY) / viewportHeight);
        viewProjection.invert(inverseViewProjection);
        nearPointClip.set(ndcX, ndcY, -1.0f, 1.0f);
        farPointClip.set(ndcX, ndcY, 1.0f, 1.0f);
        inverseViewProjection.transform(nearPointClip);
        inverseViewProjection.transform(farPointClip);
        nearPointClip.div(nearPointClip.w);
        farPointClip.div(farPointClip.w);
        rayOrigin.set(nearPointClip.x, nearPointClip.y, nearPointClip.z);
        rayDirection.set(farPointClip.x - nearPointClip.x,
                farPointClip.y - nearPointClip.y,
                farPointClip.z - nearPointClip.z).normalize();
    }

    private int findNearestHit(List<GameObject> candidates) {
        int bestIndex = -1;
        float bestDistance = NO_HIT;
        for (int i = 0; i < candidates.size(); i++) {
            float distance = intersectCandidate(candidates.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private float intersectCandidate(GameObject gameObject) {
        Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return NO_HIT;
        }
        Aabb bounds = boundsFor(gameObject);
        transform.get().localMatrix().invert(inverseModel);
        inverseModel.transformPosition(rayOrigin, localOrigin);
        inverseModel.transformDirection(rayDirection, localDirection);
        return intersectRayAabb(localOrigin, localDirection, bounds);
    }

    private Aabb boundsFor(GameObject gameObject) {
        Optional<MeshRenderer> renderer = gameObject.getComponent(MeshRenderer.class);
        if (renderer.isPresent()) {
            UploadedMesh mesh = renderer.get().mesh();
            if (mesh != null) {
                return mesh.localBounds();
            }
        }
        return NON_MESH_BOUNDS;
    }

    private static float intersectRayAabb(Vector3f origin, Vector3f direction, Aabb bounds) {
        float tMin = (bounds.minX() - origin.x) / direction.x;
        float tMax = (bounds.maxX() - origin.x) / direction.x;
        if (tMin > tMax) { float swap = tMin; tMin = tMax; tMax = swap; }
        float tyMin = (bounds.minY() - origin.y) / direction.y;
        float tyMax = (bounds.maxY() - origin.y) / direction.y;
        if (tyMin > tyMax) { float swap = tyMin; tyMin = tyMax; tyMax = swap; }
        if ((tMin > tyMax) || (tyMin > tMax)) return NO_HIT;
        if (tyMin > tMin) tMin = tyMin;
        if (tyMax < tMax) tMax = tyMax;
        float tzMin = (bounds.minZ() - origin.z) / direction.z;
        float tzMax = (bounds.maxZ() - origin.z) / direction.z;
        if (tzMin > tzMax) { float swap = tzMin; tzMin = tzMax; tzMax = swap; }
        if ((tMin > tzMax) || (tzMin > tMax)) return NO_HIT;
        if (tzMin > tMin) tMin = tzMin;
        if (tzMax < tMax) tMax = tzMax;
        if (tMax < 0.0f) return NO_HIT;
        return tMin >= 0.0f ? tMin : tMax;
    }
}
