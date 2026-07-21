package fr.epistudio.epysia.render.postfx;

import org.joml.Vector3f;

import java.util.function.Supplier;

public final class PostEffectBuiltins {

    private final Supplier<PostProcessSettings> settings;

    public PostEffectBuiltins(Supplier<PostProcessSettings> settings) {
        this.settings = settings;
    }

    public boolean isBloomEnabled() {
        return settings.get().bloomEnabled();
    }

    public PostEffectBuiltins setBloomEnabled(boolean value) {
        settings.get().setBloomEnabled(value);
        return this;
    }

    public PostEffectBuiltins setBloom(float threshold, float knee, float intensity) {
        settings.get().setBloom(threshold, knee, intensity);
        return this;
    }

    public PostEffectBuiltins setBloomThreshold(float value) {
        PostProcessSettings live = settings.get();
        live.setBloom(value, live.bloomKnee(), live.bloomIntensity());
        return this;
    }

    public PostEffectBuiltins setBloomKnee(float value) {
        PostProcessSettings live = settings.get();
        live.setBloom(live.bloomThreshold(), value, live.bloomIntensity());
        return this;
    }

    public PostEffectBuiltins setBloomIntensity(float value) {
        PostProcessSettings live = settings.get();
        live.setBloom(live.bloomThreshold(), live.bloomKnee(), value);
        return this;
    }

    public boolean isAmbientOcclusionEnabled() {
        return settings.get().ambientOcclusionEnabled();
    }

    public PostEffectBuiltins setAmbientOcclusionEnabled(boolean value) {
        settings.get().setAmbientOcclusionEnabled(value);
        return this;
    }

    public PostEffectBuiltins setAmbientOcclusion(float radius, float intensity) {
        settings.get().setAmbientOcclusion(radius, intensity);
        return this;
    }

    public PostEffectBuiltins setAmbientOcclusionPower(float value) {
        settings.get().setAmbientOcclusionPower(value);
        return this;
    }

    public boolean isAntiAliasingEnabled() {
        return settings.get().antiAliasingEnabled();
    }

    public PostEffectBuiltins setAntiAliasingEnabled(boolean value) {
        settings.get().setAntiAliasingEnabled(value);
        return this;
    }

    public float vignetteStrength() {
        return settings.get().vignetteStrength();
    }

    public PostEffectBuiltins setVignetteStrength(float value) {
        settings.get().setVignetteStrength(value);
        return this;
    }

    public boolean isFogEnabled() {
        return settings.get().fogEnabled();
    }

    public PostEffectBuiltins setFogEnabled(boolean value) {
        settings.get().setFogEnabled(value);
        return this;
    }

    public PostEffectBuiltins setFogColor(float red, float green, float blue) {
        settings.get().setFogColor(red, green, blue);
        return this;
    }

    public PostEffectBuiltins setFogColor(Vector3f color) {
        settings.get().setFogColor(color.x, color.y, color.z);
        return this;
    }

    public PostEffectBuiltins setFogDistance(float startDistance, float density) {
        settings.get().setFogDistance(startDistance, density);
        return this;
    }

    public PostEffectBuiltins setFogHeight(float originY, float falloff, float density) {
        settings.get().setFogHeight(originY, falloff, density);
        return this;
    }

    public float exposure() {
        return settings.get().gradeExposure();
    }

    public PostEffectBuiltins setExposure(float value) {
        settings.get().setGradeExposure(value);
        return this;
    }

    public float gamma() {
        return settings.get().gradeGamma();
    }

    public PostEffectBuiltins setGamma(float value) {
        settings.get().setGradeGamma(value);
        return this;
    }
}
