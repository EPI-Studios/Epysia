package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.audio.AudioLowPassFilter;
import fr.epistudio.epysia.audio.AudioSource;
import fr.epistudio.epysia.net.voice.dsp.VoiceEffect;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class VoicePlayback {
    public static final float MUFFLED_HIGH_FREQUENCY_GAIN = 0.15f;

    private static final int BUFFER_COUNT = 8;

    private final AudioSource source = new AudioSource();
    private final int[] bufferIds = new int[BUFFER_COUNT];
    private final Deque<Integer> freeBuffers = new ArrayDeque<>();
    private final ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(VoiceConfig.FRAME_SAMPLES);
    private final List<VoiceEffect> effects = new ArrayList<>();
    private AudioLowPassFilter lowPass;
    private float secondsSinceLastFrame;
    private float latestLevel;
    private float occlusionHighFrequencyGain = 1.0f;

    public VoicePlayback() {
        AL10.alGenBuffers(bufferIds);
        for (int bufferId : bufferIds) {
            freeBuffers.add(bufferId);
        }
        source.setSpatial(false);
    }

    public void submit(short[] pcm, int sampleCount) {
        recycleProcessedBuffers();
        Integer bufferId = freeBuffers.poll();
        if (bufferId == null || sampleCount <= 0) {
            return;
        }
        int usable = Math.min(sampleCount, VoiceConfig.FRAME_SAMPLES);
        for (VoiceEffect effect : effects) {
            effect.process(pcm, usable);
        }
        latestLevel = rootMeanSquareOf(pcm, usable);
        pcmBuffer.clear();
        pcmBuffer.put(pcm, 0, usable).flip();
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcmBuffer, VoiceConfig.SAMPLE_RATE);
        AL10.alSourceQueueBuffers(source.sourceId(), bufferId);
        secondsSinceLastFrame = 0.0f;
        startIfIdle();
    }

    private void startIfIdle() {
        if (!source.isPlaying()) {
            source.play();
        }
    }

    private void recycleProcessedBuffers() {
        int processed = AL10.alGetSourcei(source.sourceId(), AL10.AL_BUFFERS_PROCESSED);
        while (processed > 0) {
            freeBuffers.add(AL10.alSourceUnqueueBuffers(source.sourceId()));
            processed--;
        }
    }

    public void advance(float deltaTimeSeconds) {
        secondsSinceLastFrame += deltaTimeSeconds;
        recycleProcessedBuffers();
    }

    public float secondsSinceLastFrame() {
        return secondsSinceLastFrame;
    }

    public boolean isAudible() {
        return source.isPlaying();
    }

    public void setGain(float gain) {
        source.setGain(gain);
    }

    public float latestLevel() {
        return latestLevel;
    }

    public void setEffects(List<VoiceEffect> chain) {
        effects.clear();
        effects.addAll(chain);
        for (VoiceEffect effect : effects) {
            effect.reset();
        }
    }

    public List<VoiceEffect> effects() {
        return List.copyOf(effects);
    }

    public void routeToReverb(int auxiliaryEffectSlot) {
        source.setAuxiliaryEffectSlot(auxiliaryEffectSlot);
    }

    public void setOcclusion(float highFrequencyGain) {
        occlusionHighFrequencyGain = Math.clamp(highFrequencyGain, 0.0f, 1.0f);
        applyDirectFilter();
    }

    private void applyDirectFilter() {
        if (occlusionHighFrequencyGain >= 1.0f) {
            source.clearDirectFilter();
            return;
        }
        if (lowPass == null) {
            lowPass = new AudioLowPassFilter();
        }
        lowPass.setGains(1.0f, occlusionHighFrequencyGain);
        source.setDirectFilter(lowPass.filterId());
    }

    private static float rootMeanSquareOf(short[] samples, int count) {
        double total = 0.0;
        for (int index = 0; index < count; index++) {
            double normalized = samples[index] / (double) Short.MAX_VALUE;
            total += normalized * normalized;
        }
        return (float) Math.sqrt(total / Math.max(1, count));
    }

    public void placeAt(float x, float y, float z, VoiceConfig config) {
        source.setSpatial(true);
        source.setPosition(x, y, z);
        source.setReferenceDistance(config.referenceDistanceMeters());
        source.setMaxDistance(config.maximumDistanceMeters());
        source.setRolloffFactor(config.rolloffFactor());
    }

    public void clearPlacement() {
        source.setSpatial(false);
    }

    public void destroy() {
        source.stop();
        recycleProcessedBuffers();
        AL10.alDeleteBuffers(bufferIds);
        if (lowPass != null) {
            lowPass.destroy();
            lowPass = null;
        }
        source.destroy();
    }
}
