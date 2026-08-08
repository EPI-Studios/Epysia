package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.SphereCollider;
import fr.epistudio.epysia.render.mesh.Aabb;
import org.joml.Vector3f;

public final class ColliderFit {

    private static final float MINIMUM_EXTENT = 0.01f;

    public record Shape(Vector3f offset, Vector3f halfExtents, float radius, float halfHeight) {

        public Shape {
            offset = new Vector3f(offset);
            halfExtents = new Vector3f(halfExtents);
        }
    }

    private ColliderFit() {
    }

    public static boolean supports(IComponent component) {
        return component instanceof BoxCollider
                || component instanceof SphereCollider
                || component instanceof CapsuleCollider;
    }

    public static Shape capture(Collider collider) {
        Vector3f offset = new Vector3f(collider.offset());
        return switch (collider) {
            case BoxCollider box -> new Shape(offset, box.halfExtents(), 0.0f, 0.0f);
            case SphereCollider sphere -> new Shape(offset, new Vector3f(), sphere.radius(), 0.0f);
            case CapsuleCollider capsule ->
                    new Shape(offset, new Vector3f(), capsule.radius(), capsule.halfHeight());
            default -> new Shape(offset, new Vector3f(), 0.0f, 0.0f);
        };
    }

    public static Shape wrapping(Collider collider, Aabb bounds) {
        Vector3f center = center(bounds);
        Vector3f half = half(bounds);
        return switch (collider) {
            case SphereCollider ignored ->
                    new Shape(center, half, Math.max(half.x, Math.max(half.y, half.z)), 0.0f);
            case CapsuleCollider ignored -> capsuleWrapping(center, half);
            default -> new Shape(center, half, 0.0f, 0.0f);
        };
    }

    public static void restore(Collider collider, Shape shape) {
        Vector3f offset = shape.offset();
        collider.setOffset(offset.x, offset.y, offset.z);
        switch (collider) {
            case BoxCollider box ->
                    box.setHalfExtents(shape.halfExtents().x, shape.halfExtents().y, shape.halfExtents().z);
            case SphereCollider sphere -> sphere.setRadius(shape.radius());
            case CapsuleCollider capsule -> capsule.setCapsule(shape.radius(), shape.halfHeight());
            default -> {
            }
        }
    }

    private static Shape capsuleWrapping(Vector3f center, Vector3f half) {
        float radius = Math.max(half.x, half.z);
        return new Shape(center, half, radius, Math.max(MINIMUM_EXTENT, half.y - radius));
    }

    private static Vector3f center(Aabb bounds) {
        return new Vector3f((bounds.minX() + bounds.maxX()) * 0.5f,
                (bounds.minY() + bounds.maxY()) * 0.5f,
                (bounds.minZ() + bounds.maxZ()) * 0.5f);
    }

    private static Vector3f half(Aabb bounds) {
        return new Vector3f(halfExtent(bounds.minX(), bounds.maxX()),
                halfExtent(bounds.minY(), bounds.maxY()),
                halfExtent(bounds.minZ(), bounds.maxZ()));
    }

    private static float halfExtent(float minimum, float maximum) {
        return Math.max(MINIMUM_EXTENT, (maximum - minimum) * 0.5f);
    }
}
