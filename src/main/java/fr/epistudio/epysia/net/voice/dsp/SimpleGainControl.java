package fr.epistudio.epysia.net.voice.dsp;

final class SimpleGainControl implements GainControl {
    private static final float TARGET_LEVEL = 0.12f;
    private static final float MAXIMUM_GAIN = 8.0f;
    private static final float MINIMUM_GAIN = 0.25f;
    private static final float ATTACK = 0.25f;
    private static final float RELEASE = 0.05f;
    private static final float LIMIT = 0.98f;
    private static final float SILENCE = 1.0e-4f;

    private float gain = 1.0f;

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public void process(short[] frame, int count) {
        float level = levelOf(frame, count);
        if (level > SILENCE) {
            steerTowards(TARGET_LEVEL / level);
        }
        for (int index = 0; index < count; index++) {
            float scaled = (frame[index] / (float) Short.MAX_VALUE) * gain;
            frame[index] = (short) (Math.clamp(scaled, -LIMIT, LIMIT) * Short.MAX_VALUE);
        }
    }

    private void steerTowards(float desiredGain) {
        float clamped = Math.clamp(desiredGain, MINIMUM_GAIN, MAXIMUM_GAIN);
        float rate = clamped < gain ? ATTACK : RELEASE;
        gain += (clamped - gain) * rate;
    }

    private static float levelOf(short[] frame, int count) {
        double total = 0.0;
        for (int index = 0; index < count; index++) {
            double normalized = frame[index] / (double) Short.MAX_VALUE;
            total += normalized * normalized;
        }
        return (float) Math.sqrt(total / Math.max(1, count));
    }

    @Override
    public void close() {
    }
}
