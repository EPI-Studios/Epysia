package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform2D;
import org.joml.Vector2f;

import java.util.Optional;

@EpysiaComponent(name = "Spot Light 2D", category = "Rendering",
        description = "Cone of 2D light with an angle and a range you can set.")
@RequiresComponent(Transform2D.class)
public final class SpotLight2D extends Light2D {
    @Export(label = "Range", min = 0.0f, max = 500.0f, step = 0.1f)
    private float range = 8.0f;
    @Export(label = "Inner Angle", min = 0.0f, max = 360.0f, step = 1.0f)
    private float innerAngleDegrees = 25.0f;
    @Export(label = "Outer Angle", min = 0.0f, max = 360.0f, step = 1.0f)
    private float outerAngleDegrees = 45.0f;
    @Export(label = "Height", min = -50.0f, max = 50.0f, step = 0.05f)
    private float height = 1.0f;

    public float range() {
        return range;
    }

    public SpotLight2D setRange(float range) {
        this.range = Math.max(0.0f, range);
        return this;
    }

    public float innerAngleDegrees() {
        return innerAngleDegrees;
    }

    public SpotLight2D setInnerAngleDegrees(float degrees) {
        this.innerAngleDegrees = degrees;
        return this;
    }

    public float outerAngleDegrees() {
        return outerAngleDegrees;
    }

    public SpotLight2D setOuterAngleDegrees(float degrees) {
        this.outerAngleDegrees = degrees;
        return this;
    }

    public float height() {
        return height;
    }

    public SpotLight2D setHeight(float height) {
        this.height = height;
        return this;
    }

    public Optional<Transform2D> transform() {
        return owner().map(gameObject -> gameObject.getComponentOrNull(Transform2D.class));
    }

    public Optional<Vector2f> position() {
        return transform().map(transform -> transform.worldPosition(new Vector2f()));
    }

    public Optional<Vector2f> direction() {
        return transform().map(transform -> new Vector2f(
                (float) Math.cos(transform.worldRotationRadians()),
                (float) Math.sin(transform.worldRotationRadians())));
    }
}
