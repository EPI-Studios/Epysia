package fr.epistudio.epysia.net.voice.dsp;

final class NoNoiseSuppression implements NoiseSuppression {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public float process(short[] frame, int count) {
        return NO_ESTIMATE;
    }

    @Override
    public void close() {
    }
}
