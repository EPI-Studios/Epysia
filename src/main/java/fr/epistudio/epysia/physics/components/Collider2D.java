package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector2f;

public abstract class Collider2D extends Component {

    public static final float PLANE_HALF_DEPTH = 0.5f;

    @Export(label = "Offset X", step = 0.05f)
    private float offsetX = 0.0f;

    @Export(label = "Offset Y", step = 0.05f)
    private float offsetY = 0.0f;

    @Export(label = "Is Trigger")
    private boolean isTrigger = false;

    @Export(label = "Layer", min = 0, max = 15, step = 1)
    private int collisionLayer = 0;

    private boolean registered;

    public abstract ShapeDescriptor shape();

    public Vector2f offset() {
        return new Vector2f(offsetX, offsetY);
    }

    public Collider2D setOffset(float x, float y) {
        this.offsetX = x;
        this.offsetY = y;
        return this;
    }

    public boolean isTrigger() {
        return isTrigger;
    }

    public Collider2D setTrigger(boolean trigger) {
        this.isTrigger = trigger;
        return this;
    }

    public int collisionLayer() {
        return collisionLayer;
    }

    public Collider2D setCollisionLayer(int layer) {
        this.collisionLayer = layer;
        return this;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void markRegistered() {
        this.registered = true;
    }
}
