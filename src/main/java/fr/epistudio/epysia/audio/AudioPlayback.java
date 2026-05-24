package fr.epistudio.epysia.audio;

import org.joml.Vector3f;

public final class AudioPlayback {

    private AudioSource source;
    private final AudioBus bus;
    private float baseGain;
    private boolean spatial;
    private final Vector3f position = new Vector3f();
    private final AudioFade fade = new AudioFade();
    private Runnable onFinished;
    private boolean pendingStop;
    private boolean alive = true;

    AudioPlayback(AudioSource source, AudioBus bus, float baseGain, boolean spatial) {
        this.source = source;
        this.bus = bus;
        this.baseGain = baseGain;
        this.spatial = spatial;
    }

    public void setBaseGain(float gain) {
        this.baseGain = gain;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        if (alive && spatial) {
            source.setPosition(x, y, z);
        }
    }

    public void setOnFinished(Runnable callback) {
        this.onFinished = callback;
    }

    public void fadeOutAndStop(float seconds) {
        if (!alive) {
            return;
        }
        pendingStop = true;
        fade.start(currentFadeGain(), 0.0f, seconds, null);
    }

    public void fadeTo(float targetGainMultiplier, float seconds) {
        if (!alive) {
            return;
        }
        fade.start(currentFadeGain(), targetGainMultiplier, seconds, null);
    }

    public void stop() {
        if (!alive) {
            return;
        }
        source.stop();
    }

    public boolean isAlive() {
        return alive;
    }

    public AudioBus bus() {
        return bus;
    }

    AudioSource source() {
        return source;
    }

    AudioFade fade() {
        return fade;
    }

    float baseGain() {
        return baseGain;
    }

    boolean pendingStop() {
        return pendingStop;
    }

    boolean spatial() {
        return spatial;
    }

    Runnable onFinished() {
        return onFinished;
    }

    void markReleased() {
        alive = false;
        source = null;
    }

    private float currentFadeGain() {
        return fade.active() ? fade.currentGain() : 1.0f;
    }
}
