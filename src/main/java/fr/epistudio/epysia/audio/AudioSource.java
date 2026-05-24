package fr.epistudio.epysia.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

public final class AudioSource {

    private final int sourceId;

    public AudioSource() {
        sourceId = AL10.alGenSources();
    }

    public AudioSource setBuffer(AudioBuffer buffer) {
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, buffer == null ? 0 : buffer.bufferId());
        return this;
    }

    public AudioSource setAuxiliaryEffectSlot(int slotId) {
        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, slotId, 0, EXTEfx.AL_FILTER_NULL);
        return this;
    }

    public AudioSource resetForReuse() {
        stop();
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, 1.0f);
        AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f);
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        AL10.alSource3f(sourceId, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 0, EXTEfx.AL_FILTER_NULL);
        return this;
    }

    public AudioSource setGain(float gain) {
        AL10.alSourcef(sourceId, AL10.AL_GAIN, gain);
        return this;
    }

    public AudioSource setPitch(float pitch) {
        AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch);
        return this;
    }

    public AudioSource setLooping(boolean looping) {
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
        return this;
    }

    public AudioSource setSpatial(boolean spatial) {
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, spatial ? AL10.AL_FALSE : AL10.AL_TRUE);
        if (!spatial) {
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        }
        return this;
    }

    public AudioSource setReferenceDistance(float meters) {
        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, meters);
        return this;
    }

    public AudioSource setMaxDistance(float meters) {
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, meters);
        return this;
    }

    public AudioSource setRolloffFactor(float factor) {
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, factor);
        return this;
    }

    public AudioSource setPosition(float x, float y, float z) {
        AL10.alSource3f(sourceId, AL10.AL_POSITION, x, y, z);
        return this;
    }

    public AudioSource setVelocity(float x, float y, float z) {
        AL10.alSource3f(sourceId, AL10.AL_VELOCITY, x, y, z);
        return this;
    }

    public void play() {
        AL10.alSourcePlay(sourceId);
    }

    public void pause() {
        AL10.alSourcePause(sourceId);
    }

    public void stop() {
        AL10.alSourceStop(sourceId);
    }

    public boolean isPlaying() {
        return AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    public int sourceId() {
        return sourceId;
    }

    public void destroy() {
        AL10.alDeleteSources(sourceId);
    }
}
