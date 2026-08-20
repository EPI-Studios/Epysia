package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform2D;
import org.joml.Vector2f;

import java.util.Optional;

@EpysiaComponent(name = "Point Light 2D", category = "Rendering",
        description = "Round light that fades over its range and lights 2D sprites.")
@RequiresComponent(Transform2D.class)
public final class PointLight2D extends Light2D {
    @Export(label = "Range", min = 0.0f, max = 500.0f, step = 0.1f)
    private float range = 5.0f;
    @Export(label = "Inner Radius", min = 0.0f, max = 500.0f, step = 0.1f)
    private float innerRadius = 0.0f;
    @Export(label = "Height", min = -50.0f, max = 50.0f, step = 0.05f)
    private float height = 1.0f;

    public float range() {
        return range;
    }

    public PointLight2D setRange(float range) {
        this.range = Math.max(0.0f, range);
        return this;
    }

    public float innerRadius() {
        return innerRadius;
    }

    public PointLight2D setInnerRadius(float innerRadius) {
        this.innerRadius = Math.max(0.0f, innerRadius);
        return this;
    }

    public float height() {
        return height;
    }

    public PointLight2D setHeight(float height) {
        this.height = height;
        return this;
    }

    public Optional<Vector2f> position() {
        return owner()
                .map(gameObject -> gameObject.getComponentOrNull(Transform2D.class))
                .map(transform -> transform.worldPosition(new Vector2f()));
    }
}
