package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.diagnostics.NetworkStats;
import fr.epistudio.epysia.net.voice.dsp.VoiceEffect;
import fr.epistudio.epysia.net.voice.dsp.VoiceProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class VoiceRuntime {
    private static final float SPEAKER_IDLE_SECONDS = 2.0f;

    private final VoiceConfig config;
    private final NetworkStats stats;
    private final Logger logger;
    private final VoiceCapture capture = new VoiceCapture();
    private final VoiceActivity activity = new VoiceActivity();
    private final Map<Integer, SpeakerChannel> speakers = new LinkedHashMap<>();
    private final Set<Integer> locallyMuted = new LinkedHashSet<>();
    private final Map<Integer, Float> volumeByPeer = new LinkedHashMap<>();
    private final Map<Integer, List<Supplier<VoiceEffect>>> effectsByPeer = new LinkedHashMap<>();
    private final Map<Integer, Float> occlusionByPeer = new LinkedHashMap<>();
    private final VoiceProcessor processor = new VoiceProcessor(VoiceConfig.SAMPLE_RATE, VoiceConfig.FRAME_SAMPLES);
    private float playbackLevel;
    private final byte[] encodeScratch = new byte[VoiceConfig.MAXIMUM_PACKET_BYTES];
    private final short[] decodeScratch = new short[VoiceConfig.FRAME_SAMPLES];
    private VoiceCodec codec;
    private boolean audioAvailable;
    private boolean running;
    private int outgoingSequence;
    private int localChannelId;

    public VoiceRuntime(VoiceConfig config, NetworkStats stats, Logger logger) {
        this.config = config;
        this.stats = stats;
        this.logger = logger;
    }

    public void start(boolean wantsAudioDevices) {
        if (running) {
            return;
        }
        audioAvailable = wantsAudioDevices;
        codec = wantsAudioDevices ? VoiceCodecFactory.create(config, logger).orElse(null) : null;
        if (audioAvailable && codec != null && config.enabled() && !capture.open()) {
            logger.warn("[net.voice] no capture device is available, this peer can listen but not talk");
        }
        running = true;
    }

    public boolean codecLoaded() {
        return codec != null;
    }

    public void resetProcessing() {
        processor.reset();
        playbackLevel = 0.0f;
    }

    public void stop() {
        if (!running) {
            return;
        }
        for (SpeakerChannel channel : speakers.values()) {
            channel.destroy();
        }
        speakers.clear();
        locallyMuted.clear();
        capture.close();
        activity.close();
        processor.destroy();
        if (codec != null) {
            codec.destroy();
            codec = null;
        }
        running = false;
    }

    public String codecIdentity() {
        return config.codecIdentity();
    }

    public boolean captureAvailable() {
        return capture.isOpen();
    }

    public float inputLevel() {
        return capture.latestLevel();
    }

    public int localChannelId() {
        return localChannelId;
    }

    public void setLocalChannelId(int channelId) {
        this.localChannelId = channelId;
    }

    public List<VoiceFrame> captureOutgoing(int localPeer, boolean pushToTalkDown, float deltaTimeSeconds) {
        List<VoiceFrame> outgoing = new ArrayList<>();
        if (!running || codec == null || !config.enabled() || !capture.isOpen()) {
            return outgoing;
        }
        Optional<short[]> frame = capture.readFrame();
        while (frame.isPresent()) {
            if (shouldTransmit(frame.get(), pushToTalkDown, deltaTimeSeconds)) {
                encodeFrame(localPeer, frame.get()).ifPresent(outgoing::add);
            }
            frame = capture.readFrame();
        }
        return outgoing;
    }

    private boolean shouldTransmit(short[] frame, boolean pushToTalkDown, float deltaTimeSeconds) {
        boolean gateOpen = processor.process(frame, echoReference(), config.activationHoldSeconds(),
                config.noiseGateEnabled() && config.mode() == VoiceMode.OPEN,
                config.effectiveSpeechThreshold());
        return switch (config.mode()) {
            case MUTED -> false;
            case PUSH_TO_TALK -> pushToTalkDown;
            case OPEN -> gateOpen && activity.update(processor.outputLevel(),
                    config.activationThreshold(), config.activationHoldSeconds(), deltaTimeSeconds);
        };
    }

    private float echoReference() {
        return config.echoSuppressionEnabled() ? playbackLevel : 0.0f;
    }

    public VoiceProcessor processor() {
        return processor;
    }

    public void setVolume(int peer, float volume) {
        volumeByPeer.put(peer, Math.clamp(volume, 0.0f, 2.0f));
    }

    public float volumeOf(int peer) {
        return volumeByPeer.getOrDefault(peer, 1.0f);
    }

    public boolean anyoneSpeaking() {
        for (SpeakerChannel channel : speakers.values()) {
            if (channel.isSpeaking()) {
                return true;
            }
        }
        return false;
    }

    private Optional<VoiceFrame> encodeFrame(int localPeer, short[] pcm) {
        int written = codec.encode(pcm, encodeScratch);
        if (written <= 0) {
            return Optional.empty();
        }
        byte[] payload = new byte[written];
        System.arraycopy(encodeScratch, 0, payload, 0, written);
        stats.recordVoiceFrameSent();
        int sequence = outgoingSequence;
        outgoingSequence = (outgoingSequence + 1) % VoiceFrame.SEQUENCE_MODULUS;
        return Optional.of(new VoiceFrame(localPeer, sequence, localChannelId, payload));
    }

    private boolean isTransmitting(boolean pushToTalkDown, float deltaTimeSeconds) {
        return switch (config.mode()) {
            case MUTED -> false;
            case PUSH_TO_TALK -> pushToTalkDown;
            case OPEN -> activity.update(capture.latestLevel(), config.activationThreshold(),
                    config.activationHoldSeconds(), deltaTimeSeconds);
        };
    }

    public void receive(VoiceFrame frame) {
        if (!running || codec == null || locallyMuted.contains(frame.speakerPeer())) {
            return;
        }
        stats.recordVoiceFrameReceived();
        SpeakerChannel channel = channelFor(frame.speakerPeer());
        if (!channel.jitterBuffer().push(frame.sequence(), frame.payload())) {
            stats.recordVoiceFrameDroppedLate();
        }
        stats.recordVoiceJitterDepth(channel.jitterBuffer().depth());
    }

    private SpeakerChannel channelFor(int peer) {
        return Optional.ofNullable(speakers.get(peer)).orElseGet(() -> openChannel(peer));
    }

    private SpeakerChannel openChannel(int peer) {
        SpeakerChannel channel = new SpeakerChannel(
                new VoiceJitterBuffer(config.jitterTargetFrames()), codec.newDecoder(), audioAvailable);
        speakers.put(peer, channel);
        applyPlaybackSettings(peer, channel);
        return channel;
    }

    private void applyPlaybackSettings(int peer, SpeakerChannel channel) {
        List<VoiceEffect> chain = new ArrayList<>();
        for (Supplier<VoiceEffect> factory : effectsByPeer.getOrDefault(peer, config.effects())) {
            chain.add(factory.get());
        }
        channel.playback().ifPresent(playback -> {
            playback.setEffects(chain);
            playback.setOcclusion(occlusionByPeer.getOrDefault(peer, 1.0f));
        });
    }

    public void setEffects(int peer, List<Supplier<VoiceEffect>> chain) {
        effectsByPeer.put(peer, List.copyOf(chain));
        refreshPlayback(peer);
    }

    public void setOcclusion(int peer, float highFrequencyGain) {
        occlusionByPeer.put(peer, Math.clamp(highFrequencyGain, 0.0f, 1.0f));
        refreshPlayback(peer);
    }

    private void refreshPlayback(int peer) {
        Optional.ofNullable(speakers.get(peer)).ifPresent(channel -> applyPlaybackSettings(peer, channel));
    }

    public void refreshPlaybackSettings() {
        for (Map.Entry<Integer, SpeakerChannel> entry : speakers.entrySet()) {
            applyPlaybackSettings(entry.getKey(), entry.getValue());
        }
    }

    public void playbackTick(float deltaTimeSeconds) {
        if (!running || codec == null) {
            return;
        }
        float loudest = 0.0f;
        for (Map.Entry<Integer, SpeakerChannel> entry : speakers.entrySet()) {
            advanceSpeaker(entry.getValue(), deltaTimeSeconds);
            loudest = Math.max(loudest, levelOf(entry.getValue()));
        }
        playbackLevel = loudest;
        speakers.values().removeIf(this::retireIfIdle);
    }

    private static float levelOf(SpeakerChannel channel) {
        return channel.playback().map(VoicePlayback::latestLevel).orElse(0.0f);
    }

    private void advanceSpeaker(SpeakerChannel channel, float deltaTimeSeconds) {
        VoiceJitterBuffer.Outcome outcome = channel.jitterBuffer().pop();
        channel.setSpeaking(outcome.kind() != VoiceJitterBuffer.Kind.SILENCE);
        channel.playback().ifPresent(playback -> playback.advance(deltaTimeSeconds));
        if (outcome.kind() == VoiceJitterBuffer.Kind.SILENCE) {
            return;
        }
        int samples = outcome.kind() == VoiceJitterBuffer.Kind.PLAY
                ? channel.decoder().decode(outcome.payload(), outcome.payload().length, decodeScratch)
                : concealOneFrame(channel);
        channel.playback().ifPresent(playback -> playback.submit(decodeScratch, samples));
    }

    private int concealOneFrame(SpeakerChannel channel) {
        stats.recordVoiceFrameConcealed();
        return channel.decoder().conceal(decodeScratch);
    }

    private boolean retireIfIdle(SpeakerChannel channel) {
        boolean idle = channel.playback()
                .map(playback -> playback.secondsSinceLastFrame() > SPEAKER_IDLE_SECONDS)
                .orElse(false);
        if (idle) {
            channel.destroy();
        }
        return idle;
    }

    public Optional<VoicePlayback> playbackOf(int peer) {
        return Optional.ofNullable(speakers.get(peer)).flatMap(SpeakerChannel::playback);
    }

    public boolean isSpeaking(int peer) {
        return Optional.ofNullable(speakers.get(peer)).map(SpeakerChannel::isSpeaking).orElse(false);
    }

    public void mute(int peer, boolean muted) {
        if (muted) {
            locallyMuted.add(peer);
            Optional.ofNullable(speakers.remove(peer)).ifPresent(SpeakerChannel::destroy);
            return;
        }
        locallyMuted.remove(peer);
    }

    public boolean isMuted(int peer) {
        return locallyMuted.contains(peer);
    }

    public void forgetPeer(int peer) {
        Optional.ofNullable(speakers.remove(peer)).ifPresent(SpeakerChannel::destroy);
        locallyMuted.remove(peer);
    }

    public VoiceConfig config() {
        return config;
    }
}
