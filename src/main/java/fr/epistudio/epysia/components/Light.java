package fr.epistudio.epysia.components;

import org.joml.Vector3f;

public abstract class Light extends Component {

    @Export(label = "Color")
    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Intensity", min = 0.0f, max = 100.0f, step = 0.1f)
    private float intensity = 1.0f;
    @Export(label = "Cast Shadows")
    private boolean castShadows = true;

    public Vector3f color() {
        return color;
    }

    public float intensity() {
        return intensity;
    }

    public boolean castShadows() {
        return castShadows;
    }

    public Light setCastShadows(boolean castShadows) {
        this.castShadows = castShadows;
        return this;
    }

    public Light setColor(float red, float green, float blue) {
        color.set(red, green, blue);
        return this;
    }

    public Light setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }
}
