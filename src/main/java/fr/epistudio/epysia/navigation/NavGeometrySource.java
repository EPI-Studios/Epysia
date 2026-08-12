package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class NavGeometrySource {

    private record Piece(float[] positions, int[] indices, Matrix4f transform,
                         float minimumX, float minimumY, float minimumZ,
                         float maximumX, float maximumY, float maximumZ) {

        boolean overlaps(float[] boundsMinimum, float[] boundsMaximum) {
            return maximumX >= boundsMinimum[0] && minimumX <= boundsMaximum[0]
                    && maximumZ >= boundsMinimum[2] && minimumZ <= boundsMaximum[2];
        }
    }

    private final List<Piece> pieces = new ArrayList<>();
    private int sourceCount;

    public int sourceCount() {
        return sourceCount;
    }

    public boolean matches(Scene scene) {
        return sourceCount == countColliders(scene);
    }

    public void refresh(Scene scene) {
        pieces.clear();
        sourceCount = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            if (!gameObject.active()) {
                continue;
            }
            for (IComponent component : gameObject.components()) {
                if (component instanceof Collider collider && !collider.isTrigger()) {
                    sourceCount++;
                    appendCollider(gameObject, collider);
                }
            }
        }
    }

    public NavGeometry geometryWithin(float[] boundsMinimum, float[] boundsMaximum) {
        NavGeometry geometry = new NavGeometry();
        for (Piece piece : pieces) {
            if (piece.overlaps(boundsMinimum, boundsMaximum)) {
                geometry.addTriangles(piece.positions(), piece.indices(), piece.transform());
            }
        }
        return geometry;
    }

    public NavGeometry geometry() {
        NavGeometry geometry = new NavGeometry();
        for (Piece piece : pieces) {
            geometry.addTriangles(piece.positions(), piece.indices(), piece.transform());
        }
        return geometry;
    }

    private static int countColliders(Scene scene) {
        int total = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            if (!gameObject.active()) {
                continue;
            }
            for (IComponent component : gameObject.components()) {
                if (component instanceof Collider collider && !collider.isTrigger()) {
                    total++;
                }
            }
        }
        return total;
    }

    private void appendCollider(GameObject owner, Collider collider) {
        Matrix4f transform = worldTransformOf(owner, collider);
        switch (collider.shape()) {
            case ShapeDescriptor.TriangleMesh mesh -> addPiece(mesh.vertices(), mesh.indices(), transform);
            case ShapeDescriptor.Box box -> addBox(transform,
                    box.halfExtents().x(), box.halfExtents().y(), box.halfExtents().z());
            case ShapeDescriptor.Sphere sphere -> addBox(transform,
                    sphere.radius(), sphere.radius(), sphere.radius());
            case ShapeDescriptor.Capsule capsule -> addBox(transform,
                    capsule.radius(), capsule.halfHeight() + capsule.radius(), capsule.radius());
            case ShapeDescriptor.ConvexHull hull -> addHullBounds(transform, hull.vertices());
        }
    }

    private static Matrix4f worldTransformOf(GameObject owner, Collider collider) {
        Transform3D transform = owner.transform3DOrNull();
        Matrix4f world = transform == null ? new Matrix4f() : new Matrix4f(transform.worldMatrix());
        return world.translate(collider.offset().x(), collider.offset().y(), collider.offset().z());
    }

    private void addBox(Matrix4f transform, float halfWidth, float halfHeight, float halfDepth) {
        addPiece(NavPrimitives.boxCorners(halfWidth, halfHeight, halfDepth),
                NavPrimitives.BOX_INDICES, transform);
    }

    private void addHullBounds(Matrix4f transform, float[] vertices) {
        float halfWidth = 0.0f;
        float halfHeight = 0.0f;
        float halfDepth = 0.0f;
        for (int offset = 0; offset + 2 < vertices.length; offset += 3) {
            halfWidth = Math.max(halfWidth, Math.abs(vertices[offset]));
            halfHeight = Math.max(halfHeight, Math.abs(vertices[offset + 1]));
            halfDepth = Math.max(halfDepth, Math.abs(vertices[offset + 2]));
        }
        addBox(transform, halfWidth, halfHeight, halfDepth);
    }

    private void addPiece(float[] positions, int[] indices, Matrix4f transform) {
        if (positions.length == 0 || indices.length == 0) {
            return;
        }
        Vector3f corner = new Vector3f();
        float minimumX = Float.MAX_VALUE;
        float minimumY = Float.MAX_VALUE;
        float minimumZ = Float.MAX_VALUE;
        float maximumX = -Float.MAX_VALUE;
        float maximumY = -Float.MAX_VALUE;
        float maximumZ = -Float.MAX_VALUE;
        for (int offset = 0; offset + 2 < positions.length; offset += 3) {
            corner.set(positions[offset], positions[offset + 1], positions[offset + 2]);
            transform.transformPosition(corner);
            minimumX = Math.min(minimumX, corner.x);
            minimumY = Math.min(minimumY, corner.y);
            minimumZ = Math.min(minimumZ, corner.z);
            maximumX = Math.max(maximumX, corner.x);
            maximumY = Math.max(maximumY, corner.y);
            maximumZ = Math.max(maximumZ, corner.z);
        }
        pieces.add(new Piece(positions, indices, transform,
                minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ));
    }
}
