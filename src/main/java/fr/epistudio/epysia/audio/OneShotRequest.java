package fr.epistudio.epysia.audio;

public final class OneShotRequest {

    private AudioBuffer buffer;
    private AudioBus bus = AudioBus.SFX;
    private float gain = 1.0f;
    private float pitch = 1.0f;
    private boolean spatial;
    private float positionX;
    private float positionY;
    private float positionZ;
    private float referenceDistance = 1.0f;
    private float maxDistance = 32.0f;
    private float rolloffFactor = 1.0f;
    private float fadeInSeconds;
    private boolean useReverb = true;
    private Runnable onFinished;

    public OneShotRequest setBuffer(AudioBuffer buffer) {
        this.buffer = buffer;
        return this;
    }

    public OneShotRequest setBus(AudioBus bus) {
        this.bus = bus;
        return this;
    }

    public OneShotRequest setGain(float gain) {
        this.gain = gain;
        return this;
    }

    public OneShotRequest setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    public OneShotRequest setSpatialPosition(float x, float y, float z) {
        this.spatial = true;
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
        return this;
    }

    public OneShotRequest setDistanceFalloff(float referenceDistance, float maxDistance, float rolloffFactor) {
        this.referenceDistance = referenceDistance;
        this.maxDistance = maxDistance;
        this.rolloffFactor = rolloffFactor;
        return this;
    }

    public OneShotRequest setFadeInSeconds(float seconds) {
        this.fadeInSeconds = seconds;
        return this;
    }

    public OneShotRequest setReverbEnabled(boolean enabled) {
        this.useReverb = enabled;
        return this;
    }

    public OneShotRequest setOnFinished(Runnable callback) {
        this.onFinished = callback;
        return this;
    }

    AudioBuffer buffer() {
        return buffer;
    }

    AudioBus bus() {
        return bus;
    }

    float gain() {
        return gain;
    }

    float pitch() {
        return pitch;
    }

    boolean spatial() {
        return spatial;
    }

    float positionX() {
        return positionX;
    }

    float positionY() {
        return positionY;
    }

    float positionZ() {
        return positionZ;
    }

    float referenceDistance() {
        return referenceDistance;
    }

    float maxDistance() {
        return maxDistance;
    }

    float rolloffFactor() {
        return rolloffFactor;
    }

    float fadeInSeconds() {
        return fadeInSeconds;
    }

    boolean useReverb() {
        return useReverb;
    }

    Runnable onFinished() {
        return onFinished;
    }
}
