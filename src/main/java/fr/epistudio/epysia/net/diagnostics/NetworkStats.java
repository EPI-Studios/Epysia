package fr.epistudio.epysia.net.diagnostics;

import fr.epistudio.epysia.net.transport.NetChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

public final class NetworkStats {
    private static final float RATE_WINDOW_SECONDS = 1.0f;
    private static final int CHANNEL_COUNT = NetChannel.values().length;

    private final long[] bytesSent = new long[CHANNEL_COUNT];
    private final long[] bytesReceived = new long[CHANNEL_COUNT];
    private final long[] bytesSentThisWindow = new long[CHANNEL_COUNT];
    private final long[] bytesReceivedThisWindow = new long[CHANNEL_COUNT];
    private final long[] sendRatePerSecond = new long[CHANNEL_COUNT];
    private final long[] receiveRatePerSecond = new long[CHANNEL_COUNT];
    private final Map<Integer, PeerLatency> latencyByPeer = new LinkedHashMap<>();
    private float windowSeconds;
    private long malformedPackets;
    private long unknownMessages;
    private long oversizedMessages;
    private long rateLimitedPackets;
    private long rejectedPackets;
    private long rejectedInputs;
    private long culledObjects;
    private long rejectedRemoteProcedureCalls;
    private long reconciliationReplays;
    private long replayedTicks;
    private long missingInputs;
    private long snapshotsSent;
    private long snapshotBytes;
    private long snapshotTruncations;
    private long truncatedObjects;
    private long voiceFramesSent;
    private long voiceFramesReceived;
    private long voiceFramesDroppedLate;
    private long voiceFramesConcealed;
    private int deepestVoiceJitterBuffer;

    public void recordSent(NetChannel channel, int byteCount) {
        bytesSent[channel.ordinal()] += byteCount;
        bytesSentThisWindow[channel.ordinal()] += byteCount;
    }

    public void recordReceived(NetChannel channel, int byteCount) {
        bytesReceived[channel.ordinal()] += byteCount;
        bytesReceivedThisWindow[channel.ordinal()] += byteCount;
    }

    public void advance(float deltaTimeSeconds) {
        windowSeconds += deltaTimeSeconds;
        if (windowSeconds < RATE_WINDOW_SECONDS) {
            return;
        }
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            sendRatePerSecond[channel] = Math.round(bytesSentThisWindow[channel] / windowSeconds);
            receiveRatePerSecond[channel] = Math.round(bytesReceivedThisWindow[channel] / windowSeconds);
            bytesSentThisWindow[channel] = 0L;
            bytesReceivedThisWindow[channel] = 0L;
        }
        windowSeconds = 0.0f;
    }

    public void recordMalformedPacket() {
        malformedPackets++;
    }

    public void recordUnknownMessage() {
        unknownMessages++;
    }

    public void recordRateLimitedPacket() {
        rateLimitedPackets++;
    }

    public long rateLimitedPackets() {
        return rateLimitedPackets;
    }

    public void recordRejectedPacket() {
        rejectedPackets++;
    }

    public long rejectedPackets() {
        return rejectedPackets;
    }

    public void recordRejectedInput() {
        rejectedInputs++;
    }

    public long rejectedInputs() {
        return rejectedInputs;
    }

    public void recordCulledObjects(int count) {
        culledObjects += count;
    }

    public long culledObjects() {
        return culledObjects;
    }

    public void recordOversizedMessage() {
        oversizedMessages++;
    }

    public long oversizedMessages() {
        return oversizedMessages;
    }

    public void recordRejectedRemoteProcedureCall() {
        rejectedRemoteProcedureCalls++;
    }

    public void recordReconciliation(int ticksReplayed) {
        reconciliationReplays++;
        replayedTicks += ticksReplayed;
    }

    public void recordMissingInput() {
        missingInputs++;
    }

    public void recordSnapshot(int byteCount) {
        snapshotsSent++;
        snapshotBytes += byteCount;
    }

    public void recordSnapshotTruncation(int objectsDropped) {
        snapshotTruncations++;
        truncatedObjects += objectsDropped;
    }

    public void recordVoiceFrameSent() {
        voiceFramesSent++;
    }

    public void recordVoiceFrameReceived() {
        voiceFramesReceived++;
    }

    public void recordVoiceFrameDroppedLate() {
        voiceFramesDroppedLate++;
    }

    public void recordVoiceFrameConcealed() {
        voiceFramesConcealed++;
    }

    public void recordVoiceJitterDepth(int frames) {
        deepestVoiceJitterBuffer = Math.max(deepestVoiceJitterBuffer, frames);
    }

    public Map<String, Long> counters() {
        Map<String, Long> counters = new LinkedHashMap<>();
        for (NetChannel channel : NetChannel.values()) {
            String suffix = "_" + channel.name().toLowerCase(Locale.ROOT);
            counters.put("bytes_sent_total" + suffix, bytesSent(channel));
            counters.put("bytes_received_total" + suffix, bytesReceived(channel));
            counters.put("send_bytes_per_second" + suffix, sendRatePerSecond(channel));
            counters.put("receive_bytes_per_second" + suffix, receiveRatePerSecond(channel));
        }
        counters.put("malformed_packets_total", malformedPackets);
        counters.put("unknown_messages_total", unknownMessages);
        counters.put("oversized_messages_total", oversizedMessages);
        counters.put("rate_limited_packets_total", rateLimitedPackets);
        counters.put("rejected_packets_total", rejectedPackets);
        counters.put("rejected_inputs_total", rejectedInputs);
        counters.put("rejected_remote_procedure_calls_total", rejectedRemoteProcedureCalls);
        counters.put("reconciliation_replays_total", reconciliationReplays);
        counters.put("replayed_ticks_total", replayedTicks);
        counters.put("missing_inputs_total", missingInputs);
        counters.put("snapshots_sent_total", snapshotsSent);
        counters.put("snapshot_bytes_total", snapshotBytes);
        counters.put("snapshot_truncations_total", snapshotTruncations);
        counters.put("truncated_objects_total", truncatedObjects);
        counters.put("culled_objects_total", culledObjects);
        counters.put("voice_frames_sent_total", voiceFramesSent);
        counters.put("voice_frames_received_total", voiceFramesReceived);
        counters.put("voice_frames_dropped_late_total", voiceFramesDroppedLate);
        counters.put("voice_frames_concealed_total", voiceFramesConcealed);
        counters.put("voice_jitter_buffer_deepest", (long) deepestVoiceJitterBuffer);
        return counters;
    }

    public PeerLatency latencyOf(int peer) {
        return latencyByPeer.computeIfAbsent(peer, ignored -> new PeerLatency());
    }

    public void forgetPeer(int peer) {
        latencyByPeer.remove(peer);
    }

    public long bytesSent(NetChannel channel) {
        return bytesSent[channel.ordinal()];
    }

    public long bytesReceived(NetChannel channel) {
        return bytesReceived[channel.ordinal()];
    }

    public long sendRatePerSecond(NetChannel channel) {
        return sendRatePerSecond[channel.ordinal()];
    }

    public long receiveRatePerSecond(NetChannel channel) {
        return receiveRatePerSecond[channel.ordinal()];
    }

    public long malformedPackets() {
        return malformedPackets;
    }

    public long unknownMessages() {
        return unknownMessages;
    }

    public long rejectedRemoteProcedureCalls() {
        return rejectedRemoteProcedureCalls;
    }

    public long reconciliationReplays() {
        return reconciliationReplays;
    }

    public long replayedTicks() {
        return replayedTicks;
    }

    public long missingInputs() {
        return missingInputs;
    }

    public long snapshotsSent() {
        return snapshotsSent;
    }

    public long snapshotBytes() {
        return snapshotBytes;
    }

    public long snapshotTruncations() {
        return snapshotTruncations;
    }

    public long truncatedObjects() {
        return truncatedObjects;
    }

    public long voiceFramesSent() {
        return voiceFramesSent;
    }

    public long voiceFramesReceived() {
        return voiceFramesReceived;
    }

    public long voiceFramesDroppedLate() {
        return voiceFramesDroppedLate;
    }

    public long voiceFramesConcealed() {
        return voiceFramesConcealed;
    }

    public int deepestVoiceJitterBuffer() {
        return deepestVoiceJitterBuffer;
    }
}
