package fr.epistudio.epysia.audio;

import org.lwjgl.openal.AL10;

import java.nio.ShortBuffer;

public final class AudioBuffer {

    private final int bufferId;
    private final AudioFormat format;
    private final int sampleRate;
    private final int sampleCount;

    private AudioBuffer(int bufferId, AudioFormat format, int sampleRate, int sampleCount) {
        this.bufferId = bufferId;
        this.format = format;
        this.sampleRate = sampleRate;
        this.sampleCount = sampleCount;
    }

    public static AudioBuffer createFromPcm16(AudioFormat format, int sampleRate, ShortBuffer samples) {
        int bufferId = AL10.alGenBuffers();
        AL10.alBufferData(bufferId, format.openAlFormat(), samples, sampleRate);
        return new AudioBuffer(bufferId, format, sampleRate, samples.remaining() / format.channelCount());
    }

    public int bufferId() {
        return bufferId;
    }

    public AudioFormat format() {
        return format;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public float durationSeconds() {
        return sampleCount / (float) sampleRate;
    }

    public void destroy() {
        AL10.alDeleteBuffers(bufferId);
    }
}
