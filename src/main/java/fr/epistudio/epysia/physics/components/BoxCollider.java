package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector3f;

@EpysiaComponent(name = "Box Collider", category = "Physics")
public final class BoxCollider extends Collider {
    @Export(label = "Half Extents", min = 0.01f, step = 0.05f)
    private final Vector3f halfExtents = new Vector3f(0.5f, 0.5f, 0.5f);

    public Vector3f halfExtents() {
        return halfExtents;
    }

    public BoxCollider setHalfExtents(float x, float y, float z) {
        halfExtents.set(x, y, z);
        return this;
    }

    @Override
    public ShapeDescriptor shape() {
        return new ShapeDescriptor.Box(new Vector3f(halfExtents));
    }
}
