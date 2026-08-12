package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "World Text", category = "Rendering")
public final class WorldText extends Component {

    @Export(label = "Text")
    private String text = "";
    @Export(label = "Colour", color = true)
    private final Vector3f colour = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Opacity", min = 0.0f, max = 1.0f, step = 0.01f)
    private float opacity = 1.0f;
    @Export(label = "Line Height", min = 0.01f, max = 10.0f, step = 0.01f)
    private float lineHeight = 0.25f;
    @Export(label = "Offset X", min = -32.0f, max = 32.0f, step = 0.01f)
    private float offsetX;
    @Export(label = "Offset Y", min = -32.0f, max = 32.0f, step = 0.01f)
    private float offsetY = 2.1f;
    @Export(label = "Offset Z", min = -32.0f, max = 32.0f, step = 0.01f)
    private float offsetZ;
    @Export(label = "Hidden Behind Geometry")
    private boolean occluded = true;
    @Export(label = "Constant Screen Size")
    private boolean constantScreenSize;
    @Export(label = "Fade Start", min = 0.0f, max = 500.0f, step = 0.5f)
    private float fadeStartDistance = 25.0f;
    @Export(label = "Fade End", min = 0.0f, max = 500.0f, step = 0.5f)
    private float fadeEndDistance = 40.0f;
    @Export(label = "Outline Strength", min = 0.0f, max = 1.0f, step = 0.01f)
    private float outlineStrength = 1.0f;
    @Export(label = "Visible")
    private boolean visible = true;

    public String text() {
        return text;
    }

    public WorldText setText(String text) {
        this.text = Optional.ofNullable(text).orElse("");
        return this;
    }

    public Vector3f colour() {
        return colour;
    }

    public WorldText setColour(float red, float green, float blue) {
        colour.set(red, green, blue);
        return this;
    }

    public float opacity() {
        return opacity;
    }

    public WorldText setOpacity(float opacity) {
        this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
        return this;
    }

    public float lineHeight() {
        return lineHeight;
    }

    public WorldText setLineHeight(float lineHeight) {
        this.lineHeight = Math.max(0.001f, lineHeight);
        return this;
    }

    public WorldText setOffset(float x, float y, float z) {
        offsetX = x;
        offsetY = y;
        offsetZ = z;
        return this;
    }

    public Vector3f anchor(Vector3f destination) {
        Optional<Transform3D> transform = owner().flatMap(holder -> holder.getComponent(Transform3D.class));
        if (transform.isEmpty()) {
            return destination.set(offsetX, offsetY, offsetZ);
        }
        return transform.get().worldPosition(destination).add(offsetX, offsetY, offsetZ);
    }

    public boolean occluded() {
        return occluded;
    }

    public WorldText setOccluded(boolean occluded) {
        this.occluded = occluded;
        return this;
    }

    public boolean constantScreenSize() {
        return constantScreenSize;
    }

    public WorldText setConstantScreenSize(boolean constantScreenSize) {
        this.constantScreenSize = constantScreenSize;
        return this;
    }

    public float outlineStrength() {
        return outlineStrength;
    }

    public WorldText setOutlineStrength(float outlineStrength) {
        this.outlineStrength = Math.clamp(outlineStrength, 0.0f, 1.0f);
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public WorldText setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public WorldText setFadeDistances(float start, float end) {
        fadeStartDistance = Math.max(0.0f, start);
        fadeEndDistance = Math.max(fadeStartDistance, end);
        return this;
    }

    public float fadeFactorAt(float distance) {
        if (distance <= fadeStartDistance || fadeEndDistance <= fadeStartDistance) {
            return 1.0f;
        }
        if (distance >= fadeEndDistance) {
            return 0.0f;
        }
        return 1.0f - (distance - fadeStartDistance) / (fadeEndDistance - fadeStartDistance);
    }

    public boolean drawable() {
        return visible && !text.isEmpty() && opacity > 0.0f;
    }
}
