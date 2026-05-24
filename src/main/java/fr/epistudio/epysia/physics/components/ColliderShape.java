package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector3f;

public final class ColliderShape {

    private ColliderShape() {
    }

    public static ShapeDescriptor box(float halfExtentX, float halfExtentY, float halfExtentZ) {
        return new ShapeDescriptor.Box(new Vector3f(halfExtentX, halfExtentY, halfExtentZ));
    }

    public static ShapeDescriptor sphere(float radius) {
        return new ShapeDescriptor.Sphere(radius);
    }

    public static ShapeDescriptor capsule(float radius, float halfHeight) {
        return new ShapeDescriptor.Capsule(radius, halfHeight);
    }

    public static ShapeDescriptor triangleMesh(float[] vertices, int[] indices) {
        return new ShapeDescriptor.TriangleMesh(vertices, indices);
    }

    public static ShapeDescriptor convexHull(float[] vertices) {
        return new ShapeDescriptor.ConvexHull(vertices);
    }
}
