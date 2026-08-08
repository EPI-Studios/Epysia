package fr.epistudio.epysia.net.voice.dsp;

public final class NoiseGate {
    private static final float FLOOR_FALL_RATE = 0.30f;
    private static final float FLOOR_RISE_RATE = 0.005f;
    private static final float OPEN_RATIO = 2.5f;
    private static final float CLOSE_RATIO = 1.6f;
    private static final float INITIAL_FLOOR = 0.002f;

    private float noiseFloor = INITIAL_FLOOR;
    private float hangoverSeconds;
    private boolean open;

    public boolean update(float level, float holdSeconds, float frameSeconds) {
        trackFloor(level);
        if (level > noiseFloor * OPEN_RATIO) {
            open = true;
            hangoverSeconds = holdSeconds;
            return true;
        }
        if (open && level > noiseFloor * CLOSE_RATIO) {
            hangoverSeconds = holdSeconds;
            return true;
        }
        hangoverSeconds -= frameSeconds;
        open = hangoverSeconds > 0.0f;
        return open;
    }

    public boolean updateFromProbability(float probability, float threshold, float holdSeconds,
                                         float frameSeconds) {
        if (probability >= threshold) {
            open = true;
            hangoverSeconds = holdSeconds;
            return true;
        }
        hangoverSeconds -= frameSeconds;
        open = hangoverSeconds > 0.0f;
        return open;
    }

    private void trackFloor(float level) {
        float rate = level < noiseFloor ? FLOOR_FALL_RATE : FLOOR_RISE_RATE;
        noiseFloor += (level - noiseFloor) * rate;
        noiseFloor = Math.max(noiseFloor, 1.0e-5f);
    }

    public float noiseFloor() {
        return noiseFloor;
    }

    public void reset() {
        noiseFloor = INITIAL_FLOOR;
        hangoverSeconds = 0.0f;
        open = false;
    }
}
