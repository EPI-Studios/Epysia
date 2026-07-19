package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;

@EpysiaComponent(name = "Sphere Collider", category = "Physics")
public final class SphereCollider extends Collider {

    @Export(label = "Radius", min = 0.01f, step = 0.05f)
    private float radius = 0.5f;

    public float radius() {
        return radius;
    }

    @Override
    public ShapeDescriptor shape() {
        return new ShapeDescriptor.Sphere(radius);
    }
}
