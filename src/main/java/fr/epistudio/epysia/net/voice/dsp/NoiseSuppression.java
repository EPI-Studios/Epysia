package fr.epistudio.epysia.net.voice.dsp;

public interface NoiseSuppression extends AutoCloseable {
    float NO_ESTIMATE = -1.0f;

    String name();

    float process(short[] frame, int count);

    @Override
    void close();
}
