package fr.epistudio.epysia.physics.api;

import org.joml.Vector3fc;

public sealed interface ShapeDescriptor {

    record Box(Vector3fc halfExtents) implements ShapeDescriptor {}

    record Sphere(float radius) implements ShapeDescriptor {}

    record Capsule(float radius, float halfHeight) implements ShapeDescriptor {}

    record TriangleMesh(float[] vertices, int[] indices) implements ShapeDescriptor {}

    record ConvexHull(float[] vertices) implements ShapeDescriptor {}
}
