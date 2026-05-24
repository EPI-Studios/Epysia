package fr.epistudio.epysia.render.postfx;

import org.joml.Vector3f;

public final class PostProcessSettings {

    private float vignetteStrength = 0.45f;
    private float gradeGamma = 1.0f;
    private float gradeExposure = 1.0f;
    private boolean fogEnabled = false;
    private final Vector3f fogColor = new Vector3f(0.55f, 0.58f, 0.62f);
    private float fogDistanceDensity = 0.05f;
    private float fogDistanceStart = 2.0f;
    private float fogHeightOrigin = 1.0f;
    private float fogHeightFalloff = 0.35f;
    private float fogHeightDensity = 0.6f;

    public float vignetteStrength() {
        return vignetteStrength;
    }

    public float gradeGamma() {
        return gradeGamma;
    }

    public float gradeExposure() {
        return gradeExposure;
    }

    public boolean fogEnabled() {
        return fogEnabled;
    }

    public Vector3f fogColor() {
        return fogColor;
    }

    public float fogDistanceDensity() {
        return fogDistanceDensity;
    }

    public float fogDistanceStart() {
        return fogDistanceStart;
    }

    public float fogHeightOrigin() {
        return fogHeightOrigin;
    }

    public float fogHeightFalloff() {
        return fogHeightFalloff;
    }

    public float fogHeightDensity() {
        return fogHeightDensity;
    }

    public PostProcessSettings setVignetteStrength(float value) {
        this.vignetteStrength = value;
        return this;
    }

    public PostProcessSettings setGradeGamma(float value) {
        this.gradeGamma = value;
        return this;
    }

    public PostProcessSettings setGradeExposure(float value) {
        this.gradeExposure = value;
        return this;
    }

    public PostProcessSettings setFogEnabled(boolean value) {
        this.fogEnabled = value;
        return this;
    }

    public PostProcessSettings setFogColor(float red, float green, float blue) {
        this.fogColor.set(red, green, blue);
        return this;
    }

    public PostProcessSettings setFogDistance(float startDistance, float density) {
        this.fogDistanceStart = startDistance;
        this.fogDistanceDensity = density;
        return this;
    }

    public PostProcessSettings setFogHeight(float originY, float falloff, float density) {
        this.fogHeightOrigin = originY;
        this.fogHeightFalloff = falloff;
        this.fogHeightDensity = density;
        return this;
    }
}
