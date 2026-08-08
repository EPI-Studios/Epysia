package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

public final class ColliderOccupancyRasterizer {
    public static final int FLOATS_PER_SHAPE = 24;
    public static final int MAXIMUM_SHAPES = 1024;
    public static final int KIND_BOX = 0;
    public static final int KIND_SPHERE = 1;
    public static final int KIND_CAPSULE = 2;

    private final Matrix4f scratchWorld = new Matrix4f();
    private final Matrix4f scratchInverse = new Matrix4f();
    private final Vector3f scratchBounds = new Vector3f();

    public int pack(Scene scene, int layerMask, float[] destination) {
        List<Collider> colliders = scene.componentsOf(Collider.class);
        int written = 0;
        for (Collider collider : colliders) {
            if (written >= MAXIMUM_SHAPES || !accepts(collider, layerMask)) {
                continue;
            }
            if (packOne(collider, destination, written * FLOATS_PER_SHAPE)) {
                written++;
            }
        }
        return written;
    }

    private static boolean accepts(Collider collider, int layerMask) {
        return !collider.isTrigger() && (layerMask & (1 << collider.collisionLayer())) != 0;
    }

    private boolean packOne(Collider collider, float[] destination, int offset) {
        if (!colliderWorldMatrix(collider, scratchWorld)) {
            return false;
        }
        scratchWorld.invert(scratchInverse);
        scratchInverse.get(destination, offset);
        return writeShape(collider.shape(), destination, offset + 16);
    }

    private boolean colliderWorldMatrix(Collider collider, Matrix4f destination) {
        return collider.owner()
                .flatMap(gameObject -> gameObject.getComponent(Transform3D.class))
                .map(transform -> applyOffset(transform, collider.offset(), destination))
                .isPresent();
    }

    private static Matrix4f applyOffset(Transform3D transform, Vector3fc offset, Matrix4f destination) {
        return destination.set(transform.worldMatrix()).translate(offset);
    }

    private boolean writeShape(ShapeDescriptor shape, float[] destination, int offset) {
        return switch (shape) {
            case ShapeDescriptor.Box box -> writeBox(box.halfExtents(), destination, offset);
            case ShapeDescriptor.Sphere sphere -> writeSphere(sphere.radius(), destination, offset);
            case ShapeDescriptor.Capsule capsule ->
                    writeCapsule(capsule.radius(), capsule.halfHeight(), destination, offset);
            case ShapeDescriptor.TriangleMesh mesh -> writeBox(localBounds(mesh.vertices()), destination, offset);
            case ShapeDescriptor.ConvexHull hull -> writeBox(localBounds(hull.vertices()), destination, offset);
        };
    }

    private static boolean writeBox(Vector3fc halfExtents, float[] destination, int offset) {
        destination[offset] = halfExtents.x();
        destination[offset + 1] = halfExtents.y();
        destination[offset + 2] = halfExtents.z();
        destination[offset + 3] = 0.0f;
        destination[offset + 4] = 0.0f;
        destination[offset + 5] = KIND_BOX;
        return true;
    }

    private static boolean writeSphere(float radius, float[] destination, int offset) {
        destination[offset] = radius;
        destination[offset + 1] = radius;
        destination[offset + 2] = radius;
        destination[offset + 3] = radius;
        destination[offset + 4] = 0.0f;
        destination[offset + 5] = KIND_SPHERE;
        return true;
    }

    private static boolean writeCapsule(float radius, float halfHeight, float[] destination, int offset) {
        destination[offset] = radius;
        destination[offset + 1] = halfHeight + radius;
        destination[offset + 2] = radius;
        destination[offset + 3] = radius;
        destination[offset + 4] = halfHeight;
        destination[offset + 5] = KIND_CAPSULE;
        return true;
    }

    private Vector3fc localBounds(float[] vertices) {
        scratchBounds.zero();
        for (int index = 0; index + 2 < vertices.length; index += 3) {
            scratchBounds.set(
                    Math.max(scratchBounds.x, Math.abs(vertices[index])),
                    Math.max(scratchBounds.y, Math.abs(vertices[index + 1])),
                    Math.max(scratchBounds.z, Math.abs(vertices[index + 2])));
        }
        return scratchBounds;
    }
}
