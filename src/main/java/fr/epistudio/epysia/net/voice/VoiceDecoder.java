package fr.epistudio.epysia.net.voice;

public interface VoiceDecoder {
    int decode(byte[] packet, int length, short[] destination);

    int conceal(short[] destination);

    void destroy();
}
