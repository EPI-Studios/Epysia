package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;

@EpysiaComponent(name = "Capsule Collider", category = "Physics")
public final class CapsuleCollider extends Collider {
    @Export(label = "Radius", min = 0.01f, step = 0.05f)
    private float radius = 0.5f;

    @Export(label = "Half Height", min = 0.01f, step = 0.05f)
    private float halfHeight = 0.5f;

    public float radius() {
        return radius;
    }

    public float halfHeight() {
        return halfHeight;
    }

    public CapsuleCollider setCapsule(float newRadius, float newHalfHeight) {
        this.radius = newRadius;
        this.halfHeight = newHalfHeight;
        return this;
    }

    @Override
    public ShapeDescriptor shape() {
        return new ShapeDescriptor.Capsule(radius, halfHeight);
    }
}
