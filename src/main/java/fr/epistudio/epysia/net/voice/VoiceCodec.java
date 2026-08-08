package fr.epistudio.epysia.net.voice;

public interface VoiceCodec {
    String identity();

    int sampleRate();

    int frameSamples();

    int encode(short[] pcm, byte[] destination);

    VoiceDecoder newDecoder();

    void destroy();
}
