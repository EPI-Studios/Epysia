package fr.epistudio.epysia.net.voice.dsp;

public final class RadioColouring implements VoiceEffect {
    private static final String NAME = "radio";
    private static final float LOW_CUT_COEFFICIENT = 0.72f;
    private static final float HIGH_CUT_COEFFICIENT = 0.45f;
    private static final float DRIVE = 2.2f;
    private static final float OUTPUT_TRIM = 0.7f;

    private float lowState;
    private float highState;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void process(short[] frame, int count) {
        for (int index = 0; index < count; index++) {
            float sample = frame[index] / (float) Short.MAX_VALUE;
            sample = bandLimit(sample);
            sample = softClip(sample * DRIVE) * OUTPUT_TRIM;
            frame[index] = (short) Math.clamp(sample * Short.MAX_VALUE,
                    Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }

    private float bandLimit(float sample) {
        highState += (sample - highState) * HIGH_CUT_COEFFICIENT;
        lowState += (highState - lowState) * (1.0f - LOW_CUT_COEFFICIENT);
        return highState - lowState;
    }

    private static float softClip(float sample) {
        return (float) Math.tanh(sample);
    }

    @Override
    public void reset() {
        lowState = 0.0f;
        highState = 0.0f;
    }
}
