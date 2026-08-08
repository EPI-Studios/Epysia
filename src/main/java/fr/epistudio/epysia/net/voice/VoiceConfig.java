package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.net.voice.dsp.VoiceEffect;

import java.util.List;
import java.util.function.Supplier;

public final class VoiceConfig {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SAMPLES = 960;
    public static final int MAXIMUM_PACKET_BYTES = 512;

    private boolean enabled = true;
    private String codecIdentity = OpusVoiceCodec.IDENTITY;
    private int bitrate = 24_000;
    private VoiceMode mode = VoiceMode.PUSH_TO_TALK;
    private String pushToTalkAction = "Talk";
    private VoiceScope scope = VoiceScope.GLOBAL;
    private float hearingRadiusMeters = 20.0f;
    private int jitterTargetFrames = 3;
    private float activationThreshold = 0.02f;
    private float activationHoldSeconds = 0.35f;
    private float playbackGain = 1.0f;
    private float referenceDistanceMeters = 3.0f;
    private float maximumDistanceMeters = 30.0f;
    private float rolloffFactor = 1.0f;
    private boolean noiseGateEnabled = true;
    private float speechThreshold = 0.5f;
    private VoiceGateMode gateMode = VoiceGateMode.SPEECH_PROBABILITY;
    private boolean automaticGainEnabled = true;
    private boolean echoSuppressionEnabled = true;
    private boolean reverbSendEnabled = true;
    private List<Supplier<VoiceEffect>> effects = List.of();
    private float duckedBusGain = 1.0f;
    private float duckFadeSeconds = 0.15f;

    public boolean enabled() {
        return enabled;
    }

    public VoiceConfig setEnabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public String codecIdentity() {
        return codecIdentity;
    }

    public VoiceConfig setCodecIdentity(String value) {
        this.codecIdentity = value == null || value.isBlank() ? OpusVoiceCodec.IDENTITY : value;
        return this;
    }

    public int bitrate() {
        return bitrate;
    }

    public VoiceConfig setBitrate(int value) {
        this.bitrate = Math.clamp(value, 6_000, 128_000);
        return this;
    }

    public VoiceMode mode() {
        return mode;
    }

    public VoiceConfig setMode(VoiceMode value) {
        this.mode = value == null ? VoiceMode.MUTED : value;
        return this;
    }

    public String pushToTalkAction() {
        return pushToTalkAction;
    }

    public VoiceConfig setPushToTalkAction(String value) {
        this.pushToTalkAction = value == null ? "" : value;
        return this;
    }

    public VoiceScope scope() {
        return scope;
    }

    public VoiceConfig setScope(VoiceScope value) {
        this.scope = value == null ? VoiceScope.GLOBAL : value;
        return this;
    }

    public float hearingRadiusMeters() {
        return hearingRadiusMeters;
    }

    public VoiceConfig setHearingRadiusMeters(float value) {
        this.hearingRadiusMeters = Math.max(0.0f, value);
        return this;
    }

    public int jitterTargetFrames() {
        return jitterTargetFrames;
    }

    public VoiceConfig setJitterTargetFrames(int value) {
        this.jitterTargetFrames = Math.clamp(value, 1, 16);
        return this;
    }

    public float activationThreshold() {
        return activationThreshold;
    }

    public VoiceConfig setActivationThreshold(float value) {
        this.activationThreshold = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public float activationHoldSeconds() {
        return activationHoldSeconds;
    }

    public VoiceConfig setActivationHoldSeconds(float value) {
        this.activationHoldSeconds = Math.max(0.0f, value);
        return this;
    }

    public float playbackGain() {
        return playbackGain;
    }

    public VoiceConfig setPlaybackGain(float value) {
        this.playbackGain = Math.max(0.0f, value);
        return this;
    }

    public float referenceDistanceMeters() {
        return referenceDistanceMeters;
    }

    public VoiceConfig setReferenceDistanceMeters(float value) {
        this.referenceDistanceMeters = Math.max(0.01f, value);
        return this;
    }

    public float maximumDistanceMeters() {
        return maximumDistanceMeters;
    }

    public VoiceConfig setMaximumDistanceMeters(float value) {
        this.maximumDistanceMeters = Math.max(referenceDistanceMeters, value);
        return this;
    }

    public boolean noiseGateEnabled() {
        return noiseGateEnabled;
    }

    public VoiceConfig setNoiseGateEnabled(boolean value) {
        this.noiseGateEnabled = value;
        return this;
    }

    public VoiceGateMode gateMode() {
        return gateMode;
    }

    public VoiceConfig setGateMode(VoiceGateMode value) {
        this.gateMode = value == null ? VoiceGateMode.SPEECH_PROBABILITY : value;
        return this;
    }

    public float speechThreshold() {
        return speechThreshold;
    }

    public float effectiveSpeechThreshold() {
        return gateMode == VoiceGateMode.SPEECH_PROBABILITY ? speechThreshold : 0.0f;
    }

    public VoiceConfig setSpeechThreshold(float value) {
        this.speechThreshold = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public boolean automaticGainEnabled() {
        return automaticGainEnabled;
    }

    public VoiceConfig setAutomaticGainEnabled(boolean value) {
        this.automaticGainEnabled = value;
        return this;
    }

    public boolean echoSuppressionEnabled() {
        return echoSuppressionEnabled;
    }

    public VoiceConfig setEchoSuppressionEnabled(boolean value) {
        this.echoSuppressionEnabled = value;
        return this;
    }

    public boolean reverbSendEnabled() {
        return reverbSendEnabled;
    }

    public VoiceConfig setReverbSendEnabled(boolean value) {
        this.reverbSendEnabled = value;
        return this;
    }

    public List<Supplier<VoiceEffect>> effects() {
        return effects;
    }

    public VoiceConfig setEffects(List<Supplier<VoiceEffect>> chain) {
        this.effects = List.copyOf(chain);
        return this;
    }

    public float duckedBusGain() {
        return duckedBusGain;
    }

    public VoiceConfig setDuckedBusGain(float value) {
        this.duckedBusGain = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public float duckFadeSeconds() {
        return duckFadeSeconds;
    }

    public VoiceConfig setDuckFadeSeconds(float value) {
        this.duckFadeSeconds = Math.max(0.0f, value);
        return this;
    }

    public float rolloffFactor() {
        return rolloffFactor;
    }

    public VoiceConfig setRolloffFactor(float value) {
        this.rolloffFactor = Math.max(0.0f, value);
        return this;
    }
}
