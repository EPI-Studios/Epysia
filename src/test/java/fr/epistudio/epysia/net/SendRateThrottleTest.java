package fr.epistudio.epysia.net;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.replication.FieldSendGate;
import fr.epistudio.epysia.net.replication.ReplicationTable;
import fr.epistudio.epysia.net.replication.SnapshotInterest;
import fr.epistudio.epysia.net.replication.SnapshotPriority;
import fr.epistudio.epysia.net.replication.SnapshotRequest;
import fr.epistudio.epysia.net.replication.SnapshotWriter;
import fr.epistudio.epysia.net.replication.StateCapture;
import fr.epistudio.epysia.net.replication.WorldState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SendRateThrottleTest {
    private static final int NETWORK_ID = 3;
    private static final int PEER = 1;
    private static final int TICK_RATE = 60;
    private static final int WRITER_CAPACITY = 4096;

    private final ReplicationTable table = ReplicationTable.builder()
            .addComponentType(ReplicatedStats.class)
            .build();
    private final SnapshotWriter snapshotWriter = new SnapshotWriter(table);
    private final StateCapture capture = new StateCapture(table);
    private final FieldSendGate gate = FieldSendGate.throttled();

    private final GameObject holder = new GameObject("throttled");
    private final ReplicatedStats stats = holder.addComponent(new ReplicatedStats());

    @Test
    void aFieldWithoutASendRateGoesOutEveryTickItChanges() {
        int sent = countTicksWhereFieldChanged(false);
        assertEquals(10, sent, "health has no send rate so every change should go out");
    }

    @Test
    void aFiveHertzFieldGoesOutOnceEveryTwelveTicks() {
        int sent = countTicksWhereFieldChanged(true);
        assertEquals(1, sent, "displayName is 5 Hz, so at 60 Hz it may only pass once in ten ticks");
    }

    private int countTicksWhereFieldChanged(boolean throttledField) {
        WorldState baseline = new WorldState();
        int accepted = 0;
        for (int tick = 0; tick < 10; tick++) {
            mutate(throttledField, tick);
            WorldState current = new WorldState();
            capture.capture(holder, NETWORK_ID, current);
            NetWriter writer = NetWriter.allocate(WRITER_CAPACITY);
            WorldState delivered = snapshotWriter.write(writer, requestFor(current, baseline, tick));
            if (!snapshotWriter.writtenNetworkIds().isEmpty()) {
                accepted++;
            }
            baseline = delivered;
        }
        return accepted;
    }

    private void mutate(boolean throttledField, int tick) {
        if (throttledField) {
            stats.setDisplayName("name" + tick);
            return;
        }
        stats.setHealth(tick);
    }

    private SnapshotRequest requestFor(WorldState current, WorldState baseline, int tick) {
        return new SnapshotRequest(current, baseline, Map.of(NETWORK_ID, PEER), PEER, tick,
                tick == 0 ? SnapshotRequest.NO_BASELINE : tick - 1,
                SnapshotRequest.NO_CEILING, SnapshotPriority.NONE, gate, TICK_RATE,
                SnapshotInterest.EVERYTHING);
    }
}
