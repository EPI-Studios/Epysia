package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.net.voice.dsp.VoiceEffect;

import java.util.List;
import java.util.function.Supplier;

public final class VoiceService {
    private final VoiceConfig config;
    private final VoiceRuntime runtime;
    private final VoiceChannelAssignment channels;

    public VoiceService(VoiceConfig config, VoiceRuntime runtime, VoiceChannelAssignment channels) {
        this.config = config;
        this.runtime = runtime;
        this.channels = channels;
    }

    public VoiceConfig config() {
        return config;
    }

    public VoiceService setMode(VoiceMode mode) {
        config.setMode(mode);
        return this;
    }

    public VoiceService setPushToTalkAction(String actionName) {
        config.setPushToTalkAction(actionName);
        return this;
    }

    public VoiceService setScope(VoiceScope scope) {
        config.setScope(scope);
        return this;
    }

    public VoiceService setHearingRadiusMeters(float meters) {
        config.setHearingRadiusMeters(meters);
        return this;
    }

    public VoiceService setChannel(int peer, int channelId) {
        channels.assign(peer, channelId);
        return this;
    }

    public int channelOf(int peer) {
        return channels.channelOf(peer);
    }

    public VoiceService mute(int peer, boolean muted) {
        runtime.mute(peer, muted);
        return this;
    }

    public boolean isMuted(int peer) {
        return runtime.isMuted(peer);
    }

    public VoiceService setVolume(int peer, float volume) {
        runtime.setVolume(peer, volume);
        return this;
    }

    public float volumeOf(int peer) {
        return runtime.volumeOf(peer);
    }

    public VoiceService setEffects(List<Supplier<VoiceEffect>> chain) {
        config.setEffects(chain);
        runtime.refreshPlaybackSettings();
        return this;
    }

    public VoiceService setEffects(int peer, List<Supplier<VoiceEffect>> chain) {
        runtime.setEffects(peer, chain);
        return this;
    }

    public VoiceService setOcclusion(int peer, float highFrequencyGain) {
        runtime.setOcclusion(peer, highFrequencyGain);
        return this;
    }

    public VoiceService setNoiseGateEnabled(boolean enabled) {
        config.setNoiseGateEnabled(enabled);
        return this;
    }

    public VoiceService setAutomaticGainEnabled(boolean enabled) {
        config.setAutomaticGainEnabled(enabled);
        return this;
    }

    public VoiceService setEchoSuppressionEnabled(boolean enabled) {
        config.setEchoSuppressionEnabled(enabled);
        return this;
    }

    public VoiceService setReverbSendEnabled(boolean enabled) {
        config.setReverbSendEnabled(enabled);
        return this;
    }

    public VoiceService setDuckedBusGain(float gain, float fadeSeconds) {
        config.setDuckedBusGain(gain);
        config.setDuckFadeSeconds(fadeSeconds);
        return this;
    }

    public VoiceService setGateMode(VoiceGateMode mode) {
        config.setGateMode(mode);
        return this;
    }

    public VoiceService setSpeechThreshold(float threshold) {
        config.setSpeechThreshold(threshold);
        return this;
    }

    public float speechProbability() {
        return runtime.processor().speechProbability();
    }

    public String noiseSuppressionName() {
        return runtime.processor().noiseSuppressionName();
    }

    public String gainName() {
        return runtime.processor().gainName();
    }

    public float noiseFloor() {
        return runtime.processor().noiseFloor();
    }

    public boolean suppressingEcho() {
        return runtime.processor().suppressingEcho();
    }

    public boolean isSpeaking(int peer) {
        return runtime.isSpeaking(peer);
    }

    public boolean captureAvailable() {
        return runtime.captureAvailable();
    }

    public float inputLevel() {
        return runtime.inputLevel();
    }

    public String codecIdentity() {
        return runtime.codecIdentity();
    }
}
