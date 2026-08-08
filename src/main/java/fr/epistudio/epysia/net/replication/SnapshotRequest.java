package fr.epistudio.epysia.net.replication;

import java.util.Map;

public record SnapshotRequest(
        WorldState current,
        WorldState baseline,
        Map<Integer, Integer> ownerByNetworkId,
        int targetPeer,
        int serverTick,
        int baselineTick,
        int byteCeiling,
        SnapshotPriority priority,
        FieldSendGate sendGate,
        int tickRate,
        SnapshotInterest interest
) {
    public static final int NO_BASELINE = -1;
    public static final int NO_CEILING = Integer.MAX_VALUE;
    public static final int DEFAULT_TICK_RATE = 60;

    public SnapshotRequest(WorldState current, WorldState baseline, Map<Integer, Integer> ownerByNetworkId,
                           int targetPeer, int serverTick, int baselineTick) {
        this(current, baseline, ownerByNetworkId, targetPeer, serverTick, baselineTick,
                NO_CEILING, SnapshotPriority.NONE, FieldSendGate.ALWAYS_OPEN, DEFAULT_TICK_RATE,
                SnapshotInterest.EVERYTHING);
    }

    public SnapshotRequest(WorldState current, WorldState baseline, Map<Integer, Integer> ownerByNetworkId,
                           int targetPeer, int serverTick, int baselineTick, int byteCeiling,
                           SnapshotPriority priority) {
        this(current, baseline, ownerByNetworkId, targetPeer, serverTick, baselineTick,
                byteCeiling, priority, FieldSendGate.ALWAYS_OPEN, DEFAULT_TICK_RATE,
                SnapshotInterest.EVERYTHING);
    }

    public boolean isFull() {
        return baselineTick == NO_BASELINE;
    }

    public boolean targetOwns(int networkId) {
        return ownerByNetworkId.getOrDefault(networkId, NetworkObject.SERVER_PEER) == targetPeer;
    }
}
