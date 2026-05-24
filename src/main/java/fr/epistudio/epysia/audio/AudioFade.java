package fr.epistudio.epysia.audio;

public final class AudioFade {

    private float startGain;
    private float targetGain;
    private float durationSeconds;
    private float elapsedSeconds;
    private boolean active;
    private Runnable onComplete;

    public void start(float from, float to, float seconds, Runnable onComplete) {
        this.startGain = from;
        this.targetGain = to;
        this.durationSeconds = Math.max(0.0001f, seconds);
        this.elapsedSeconds = 0.0f;
        this.active = true;
        this.onComplete = onComplete;
    }

    public float advance(float deltaTimeSeconds) {
        if (!active) {
            return targetGain;
        }
        elapsedSeconds += deltaTimeSeconds;
        float t = Math.min(1.0f, elapsedSeconds / durationSeconds);
        float current = startGain + (targetGain - startGain) * t;
        if (t >= 1.0f) {
            active = false;
            if (onComplete != null) {
                Runnable callback = onComplete;
                onComplete = null;
                callback.run();
            }
        }
        return current;
    }

    public boolean active() {
        return active;
    }

    public float currentGain() {
        if (!active) {
            return targetGain;
        }
        float t = Math.min(1.0f, elapsedSeconds / durationSeconds);
        return startGain + (targetGain - startGain) * t;
    }

    public float targetGain() {
        return targetGain;
    }
}
