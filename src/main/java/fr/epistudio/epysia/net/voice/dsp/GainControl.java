package fr.epistudio.epysia.net.voice.dsp;

public interface GainControl extends AutoCloseable {
    String name();

    void process(short[] frame, int count);

    @Override
    void close();
}
