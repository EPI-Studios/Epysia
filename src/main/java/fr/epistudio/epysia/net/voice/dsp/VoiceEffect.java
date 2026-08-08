package fr.epistudio.epysia.net.voice.dsp;

public interface VoiceEffect {
    String name();

    void process(short[] frame, int count);

    default void reset() {
    }
}
