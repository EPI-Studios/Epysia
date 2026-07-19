package fr.epistudio.epysia.render.environment;

public final class SkySettings {

    private float skyIntensity = 1.0f;
    private float ambientIntensity = 1.0f;

    public float skyIntensity() {
        return skyIntensity;
    }

    public float ambientIntensity() {
        return ambientIntensity;
    }

    public SkySettings setSkyIntensity(float value) {
        this.skyIntensity = value;
        return this;
    }

    public SkySettings setAmbientIntensity(float value) {
        this.ambientIntensity = value;
        return this;
    }
}
