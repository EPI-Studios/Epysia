package fr.epistudio.epysia.net.diagnostics;

public final class PeerLatency {
    private static final float SMOOTHING = 0.1f;

    private float roundTripSeconds;
    private float jitterSeconds;
    private boolean sampled;

    public void sample(float measuredRoundTripSeconds) {
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

    public float jitterSeconds() {
        return jitterSeconds;
    }

    public boolean sampled() {
        return sampled;
    }
}
