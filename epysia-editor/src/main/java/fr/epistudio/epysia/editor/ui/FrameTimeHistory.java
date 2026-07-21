package fr.epistudio.epysia.editor.ui;

public final class FrameTimeHistory {

    private final float[] samples;
    private int cursor;
    private int filled;

    public FrameTimeHistory(int length) {
        samples = new float[length];
    }

    public void record(float milliseconds) {
        samples[cursor] = milliseconds;
        cursor = (cursor + 1) % samples.length;
        filled = Math.min(filled + 1, samples.length);
    }

    public float[] samples() {
        return samples;
    }

    public int length() {
        return samples.length;
    }

    public int cursor() {
        return cursor;
    }

    public float latest() {
        int lastIndex = (cursor - 1 + samples.length) % samples.length;
        return filled == 0 ? 0.0f : samples[lastIndex];
    }

    public float minimum() {
        float minimum = Float.MAX_VALUE;
        for (int index = 0; index < filled; index++) {
            minimum = Math.min(minimum, samples[index]);
        }
        return filled == 0 ? 0.0f : minimum;
    }

    public float maximum() {
        float maximum = 0.0f;
        for (int index = 0; index < filled; index++) {
            maximum = Math.max(maximum, samples[index]);
        }
        return maximum;
    }

    public float average() {
        float total = 0.0f;
        for (int index = 0; index < filled; index++) {
            total += samples[index];
        }
        return filled == 0 ? 0.0f : total / filled;
    }
}
