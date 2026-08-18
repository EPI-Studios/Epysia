package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector2f;
import org.joml.Vector3f;

@EpysiaComponent(name = "Box Collider 2D", category = "Physics",
        description = "Rectangular collision shape.")
public final class BoxCollider2D extends Collider2D {

    @Export(label = "Half Extent X", min = 0.01f, step = 0.05f)
    private float halfExtentX = 0.5f;

    @Export(label = "Half Extent Y", min = 0.01f, step = 0.05f)
    private float halfExtentY = 0.5f;

    public Vector2f halfExtents() {
        return new Vector2f(halfExtentX, halfExtentY);
    }

    public BoxCollider2D setHalfExtents(float x, float y) {
        this.halfExtentX = x;
        this.halfExtentY = y;
        return this;
    }

    @Override
    public ShapeDescriptor shape() {
        return new ShapeDescriptor.Box(new Vector3f(halfExtentX, halfExtentY, PLANE_HALF_DEPTH));
    }
}
