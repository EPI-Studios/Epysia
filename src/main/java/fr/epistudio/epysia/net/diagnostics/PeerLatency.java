package fr.epistudio.epysia.net.diagnostics;

public final class PeerLatency {
    private static final float SMOOTHING = 0.1f;
    private static final int WINDOW_SAMPLES = 24;

    private final float[] window = new float[WINDOW_SAMPLES];
    private int windowCount;
    private int windowCursor;
    private float roundTripSeconds;
    private float jitterSeconds;
    private boolean sampled;

    public void sample(float measuredRoundTripSeconds) {
        window[windowCursor] = measuredRoundTripSeconds;
        windowCursor = (windowCursor + 1) % WINDOW_SAMPLES;
        windowCount = Math.min(WINDOW_SAMPLES, windowCount + 1);
        if (!sampled) {
            roundTripSeconds = measuredRoundTripSeconds;
            sampled = true;
            return;
        }
        float deviation = Math.abs(measuredRoundTripSeconds - roundTripSeconds);
        jitterSeconds += (deviation - jitterSeconds) * SMOOTHING;
        roundTripSeconds += (measuredRoundTripSeconds - roundTripSeconds) * SMOOTHING;
    }

    public float roundTripSeconds() {
        return roundTripSeconds;
    }

    public float stableRoundTripSeconds() {
        if (windowCount == 0) {
            return roundTripSeconds;
        }
        float lowest = Float.MAX_VALUE;
        for (int index = 0; index < windowCount; index++) {
            lowest = Math.min(lowest, window[index]);
        }
        return lowest;
    }

    public float jitterSeconds() {
        return jitterSeconds;
    }

    public boolean sampled() {
        return sampled;
    }
}
