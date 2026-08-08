package fr.epistudio.epysia.net.voice;

public final class VoiceActivity {
    private float secondsSinceAboveThreshold = Float.MAX_VALUE;
    private boolean open;

    public boolean update(float level, float threshold, float holdSeconds, float deltaTimeSeconds) {
        if (level >= threshold) {
            secondsSinceAboveThreshold = 0.0f;
            open = true;
            return true;
        }
        secondsSinceAboveThreshold += deltaTimeSeconds;
        open = secondsSinceAboveThreshold <= holdSeconds;
        return open;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        secondsSinceAboveThreshold = Float.MAX_VALUE;
    }
}
