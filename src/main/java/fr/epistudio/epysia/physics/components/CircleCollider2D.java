package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;

@EpysiaComponent(name = "Circle Collider 2D", category = "Physics",
        description = "Round collision shape.")
public final class CircleCollider2D extends Collider2D {

    @Export(label = "Radius", min = 0.01f, step = 0.05f)
    private float radius = 0.5f;

    public float radius() {
        return radius;
    }

    public CircleCollider2D setRadius(float radius) {
        this.radius = radius;
        return this;
    }

    @Override
    public ShapeDescriptor shape() {
        return new ShapeDescriptor.Sphere(radius);
    }
}
