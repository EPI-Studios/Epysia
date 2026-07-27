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
    private boolean bloomEnabled = false;
    private float bloomThreshold = 1.0f;
    private float bloomKnee = 0.5f;
    private float bloomIntensity = 0.35f;
    private boolean ambientOcclusionEnabled = false;
    private float ambientOcclusionRadius = 0.5f;
    private float ambientOcclusionIntensity = 1.0f;
    private float ambientOcclusionPower = 1.5f;
    private boolean ambientOcclusionFullResolution = false;
    private boolean pixelPerfectEnabled = false;
    private int pixelPerfectBaseHeight = 270;
    private int pixelPerfectBaseWidth = 480;
    private boolean pixelPerfectIntegerScale = true;
    private StretchAspect pixelPerfectAspect = StretchAspect.KEEP;
    private boolean antiAliasingEnabled = true;
    private String fogShaderPath = "";

    public boolean bloomEnabled() {
        return bloomEnabled;
    }

    public float bloomThreshold() {
        return bloomThreshold;
    }

    public float bloomKnee() {
        return bloomKnee;
    }

    public float bloomIntensity() {
        return bloomIntensity;
    }

    public boolean ambientOcclusionEnabled() {
        return ambientOcclusionEnabled;
    }

    public float ambientOcclusionRadius() {
        return ambientOcclusionRadius;
    }

    public float ambientOcclusionIntensity() {
        return ambientOcclusionIntensity;
    }

    public float ambientOcclusionPower() {
        return ambientOcclusionPower;
    }

    public PostProcessSettings copyFrom(PostProcessSettings other) {
        vignetteStrength = other.vignetteStrength;
        gradeGamma = other.gradeGamma;
        gradeExposure = other.gradeExposure;
        fogEnabled = other.fogEnabled;
        fogColor.set(other.fogColor);
        copyFogShape(other);
        copyBloom(other);
        copyAmbientOcclusion(other);
        pixelPerfectEnabled = other.pixelPerfectEnabled;
        pixelPerfectBaseHeight = other.pixelPerfectBaseHeight;
        pixelPerfectBaseWidth = other.pixelPerfectBaseWidth;
        pixelPerfectIntegerScale = other.pixelPerfectIntegerScale;
        pixelPerfectAspect = other.pixelPerfectAspect;
        antiAliasingEnabled = other.antiAliasingEnabled;
        fogShaderPath = other.fogShaderPath;
        return this;
    }

    private void copyFogShape(PostProcessSettings other) {
        fogDistanceDensity = other.fogDistanceDensity;
        fogDistanceStart = other.fogDistanceStart;
        fogHeightOrigin = other.fogHeightOrigin;
        fogHeightFalloff = other.fogHeightFalloff;
        fogHeightDensity = other.fogHeightDensity;
    }

    private void copyBloom(PostProcessSettings other) {
        bloomEnabled = other.bloomEnabled;
        bloomThreshold = other.bloomThreshold;
        bloomKnee = other.bloomKnee;
        bloomIntensity = other.bloomIntensity;
    }

    private void copyAmbientOcclusion(PostProcessSettings other) {
        ambientOcclusionEnabled = other.ambientOcclusionEnabled;
        ambientOcclusionRadius = other.ambientOcclusionRadius;
        ambientOcclusionIntensity = other.ambientOcclusionIntensity;
        ambientOcclusionPower = other.ambientOcclusionPower;
        ambientOcclusionFullResolution = other.ambientOcclusionFullResolution;
    }

    public boolean pixelPerfectEnabled() {
        return pixelPerfectEnabled;
    }

    public PostProcessSettings setPixelPerfectEnabled(boolean value) {
        pixelPerfectEnabled = value;
        return this;
    }

    public int pixelPerfectBaseWidth() {
        return pixelPerfectBaseWidth;
    }

    public PostProcessSettings setPixelPerfectBaseWidth(int value) {
        pixelPerfectBaseWidth = Math.clamp(value, 32, 7680);
        return this;
    }

    public boolean pixelPerfectIntegerScale() {
        return pixelPerfectIntegerScale;
    }

    public PostProcessSettings setPixelPerfectIntegerScale(boolean value) {
        pixelPerfectIntegerScale = value;
        return this;
    }

    public StretchAspect pixelPerfectAspect() {
        return pixelPerfectAspect;
    }

    public PostProcessSettings setPixelPerfectAspect(StretchAspect value) {
        pixelPerfectAspect = value == null ? StretchAspect.KEEP : value;
        return this;
    }

    public int pixelPerfectBaseHeight() {
        return pixelPerfectBaseHeight;
    }

    public PostProcessSettings setPixelPerfectBaseHeight(int value) {
        pixelPerfectBaseHeight = Math.clamp(value, 32, 2160);
        return this;
    }

    public boolean ambientOcclusionFullResolution() {
        return ambientOcclusionFullResolution;
    }

    public String fogShaderPath() {
        return fogShaderPath;
    }

    public PostProcessSettings setFogShaderPath(String value) {
        fogShaderPath = value == null ? "" : value;
        return this;
    }

    public boolean antiAliasingEnabled() {
        return antiAliasingEnabled;
    }

    public PostProcessSettings setBloomEnabled(boolean value) {
        this.bloomEnabled = value;
        return this;
    }

    public PostProcessSettings setBloom(float threshold, float knee, float intensity) {
        this.bloomThreshold = threshold;
        this.bloomKnee = knee;
        this.bloomIntensity = intensity;
        return this;
    }

    public PostProcessSettings setAmbientOcclusionEnabled(boolean value) {
        this.ambientOcclusionEnabled = value;
        return this;
    }

    public PostProcessSettings setAmbientOcclusion(float radius, float intensity) {
        this.ambientOcclusionRadius = radius;
        this.ambientOcclusionIntensity = intensity;
        return this;
    }

    public PostProcessSettings setAmbientOcclusionPower(float value) {
        this.ambientOcclusionPower = value;
        return this;
    }

    public PostProcessSettings setAmbientOcclusionFullResolution(boolean value) {
        this.ambientOcclusionFullResolution = value;
        return this;
    }

    public PostProcessSettings setAntiAliasingEnabled(boolean value) {
        this.antiAliasingEnabled = value;
        return this;
    }

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
