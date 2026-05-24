package fr.epistudio.epysia.audio;

public final class AudioStream {

    private AudioSource source;
    private final StreamingAudioSource streaming;
    private final AudioBus bus;
    private float baseGain;
    private final AudioFade fade = new AudioFade();
    private Runnable onFinished;
    private boolean alive = true;
    private boolean pendingStop;

    AudioStream(AudioSource source, StreamingAudioSource streaming, AudioBus bus, float baseGain) {
        this.source = source;
        this.streaming = streaming;
        this.bus = bus;
        this.baseGain = baseGain;
    }

    public void setBaseGain(float gain) {
        this.baseGain = gain;
    }

    public void setOnFinished(Runnable callback) {
        this.onFinished = callback;
    }

    public void fadeOutAndStop(float seconds) {
        if (!alive) {
            return;
        }
        pendingStop = true;
        fade.start(fade.active() ? fade.currentGain() : 1.0f, 0.0f, seconds, null);
    }

    public void fadeTo(float targetMultiplier, float seconds) {
        if (!alive) {
            return;
        }
        fade.start(fade.active() ? fade.currentGain() : 1.0f, targetMultiplier, seconds, null);
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

    StreamingAudioSource streaming() {
        return streaming;
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

    Runnable onFinished() {
        return onFinished;
    }

    void markReleased() {
        alive = false;
        source = null;
    }
}
