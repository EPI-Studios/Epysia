package fr.epistudio.epysia.net.voice.dsp;

public final class HighPassFilter {
    private static final float CUTOFF_HERTZ = 90.0f;

    private final float coefficient;
    private float previousInput;
    private float previousOutput;

    public HighPassFilter(int sampleRate) {
        float timeConstant = 1.0f / (2.0f * (float) Math.PI * CUTOFF_HERTZ);
        float sampleInterval = 1.0f / sampleRate;
        this.coefficient = timeConstant / (timeConstant + sampleInterval);
    }

    public void process(float[] samples, int count) {
        for (int index = 0; index < count; index++) {
            float input = samples[index];
            float output = coefficient * (previousOutput + input - previousInput);
            previousInput = input;
            previousOutput = output;
            samples[index] = output;
        }
    }

    public void reset() {
        previousInput = 0.0f;
        previousOutput = 0.0f;
    }
}
