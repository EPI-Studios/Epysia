package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.net.prediction.InputRing;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.replication.FieldSendGate;
import fr.epistudio.epysia.net.replication.SnapshotRequest;
import fr.epistudio.epysia.net.replication.WorldState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class NetworkPeer {
    private static final int RETAINED_BASELINES = 64;

    private final int id;
    private final int connection;
    private final TreeMap<Integer, WorldState> sentStatesByTick = new TreeMap<>();
    private final Map<Integer, Integer> lastIncludedTickByObject = new HashMap<>();
    private String displayName = "player";
    private float secondsSinceLastPacket;
    private int acknowledgedSnapshotTick = SnapshotRequest.NO_BASELINE;
    private final InputRing pendingInputs = new InputRing();
    private final FieldSendGate sendGate = FieldSendGate.throttled();
    private int lastAppliedInputTick = -1;
    private InputSample currentInput = InputSample.empty(-1, 0);
    private boolean currentInputRepeated;
    private int voiceChannel;
    private boolean handshakeComplete;
    private long reconnectToken;

    public NetworkPeer(int id, int connection) {
        this.id = id;
        this.connection = connection;
    }

    public int id() {
        return id;
    }

    public int connection() {
        return connection;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        this.displayName = value == null || value.isBlank() ? "player" : value;
    }

    public long reconnectToken() {
        return reconnectToken;
    }

    public void setReconnectToken(long token) {
        this.reconnectToken = token;
    }

    public boolean handshakeComplete() {
        return handshakeComplete;
    }

    public void markHandshakeComplete() {
        this.handshakeComplete = true;
    }

    public void noteTraffic() {
        secondsSinceLastPacket = 0.0f;
    }

    public boolean hasTimedOut(float deltaTimeSeconds, float timeoutSeconds) {
        secondsSinceLastPacket += deltaTimeSeconds;
        return secondsSinceLastPacket >= timeoutSeconds;
    }

    public int acknowledgedSnapshotTick() {
        return acknowledgedSnapshotTick;
    }

    public void acknowledgeSnapshot(int tick) {
        if (tick > acknowledgedSnapshotTick) {
            acknowledgedSnapshotTick = tick;
            sentStatesByTick.headMap(tick, false).clear();
        }
    }

    public Optional<WorldState> baselineForAcknowledgedTick() {
        return Optional.ofNullable(sentStatesByTick.get(acknowledgedSnapshotTick));
    }

    public void recordSentState(int tick, WorldState state) {
        sentStatesByTick.put(tick, state);
        while (sentStatesByTick.size() > RETAINED_BASELINES) {
            sentStatesByTick.pollFirstEntry();
        }
    }

    public int lastAppliedInputTick() {
        return lastAppliedInputTick;
    }

    public void setLastAppliedInputTick(int tick) {
        this.lastAppliedInputTick = tick;
    }

    public InputSample currentInput() {
        return currentInput;
    }

    public boolean currentInputRepeated() {
        return currentInputRepeated;
    }

    public FieldSendGate sendGate() {
        return sendGate;
    }

    public boolean offerInput(InputSample sample, int serverTick, int maximumLeadTicks) {
        if (sample.tick() <= lastAppliedInputTick) {
            return false;
        }
        if (sample.tick() > serverTick + maximumLeadTicks || sample.tick() < serverTick - maximumLeadTicks) {
            return false;
        }
        pendingInputs.push(sample);
        return true;
    }

    public boolean resolveInputFor(int tick) {
        Optional<InputSample> arrived = pendingInputs.at(tick);
        if (arrived.isPresent()) {
            currentInput = arrived.get();
            lastAppliedInputTick = tick;
            currentInputRepeated = false;
            pendingInputs.acknowledgeThrough(tick);
            return true;
        }
        currentInput = currentInput.retimed(tick);
        currentInputRepeated = true;
        return false;
    }

    public int pendingInputCount() {
        return pendingInputs.size();
    }

    public int voiceChannel() {
        return voiceChannel;
    }

    public void setVoiceChannel(int channel) {
        this.voiceChannel = channel;
    }

    public void markIncluded(int networkId, int tick) {
        lastIncludedTickByObject.put(networkId, tick);
    }

    public int ticksSinceIncluded(int networkId, int currentTick) {
        return currentTick - lastIncludedTickByObject.getOrDefault(networkId, 0);
    }

    public void forgetObject(int networkId) {
        lastIncludedTickByObject.remove(networkId);
        sendGate.forget(networkId);
    }

    public int rememberedObjectCount() {
        return lastIncludedTickByObject.size();
    }

    public void forgetBaselines() {
        lastIncludedTickByObject.clear();
        sendGate.clear();
        sentStatesByTick.clear();
        acknowledgedSnapshotTick = SnapshotRequest.NO_BASELINE;
    }
}
