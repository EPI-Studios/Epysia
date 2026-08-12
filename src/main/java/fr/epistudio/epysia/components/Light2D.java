package fr.epistudio.epysia.components;

import org.joml.Vector3f;

public abstract class Light2D extends Component {

    public static final int ALL_LIGHT_LAYERS = -1;

    @Export(label = "Color", color = true)
    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Intensity", min = 0.0f, max = 100.0f, step = 0.1f)
    private float intensity = 1.0f;
    @Export(label = "Light Layers", step = 1.0f)
    private int lightLayers = ALL_LIGHT_LAYERS;

    public Vector3f color() {
        return color;
    }

    public Light2D setColor(float red, float green, float blue) {
        color.set(red, green, blue);
        return this;
    }

    public float intensity() {
        return intensity;
    }

    public Light2D setIntensity(float intensity) {
        this.intensity = Math.max(0.0f, intensity);
        return this;
    }

    public int lightLayers() {
        return lightLayers;
    }

    public Light2D setLightLayers(int lightLayers) {
        this.lightLayers = lightLayers;
        return this;
    }
}
