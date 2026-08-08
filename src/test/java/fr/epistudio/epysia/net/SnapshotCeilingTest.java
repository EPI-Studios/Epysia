package fr.epistudio.epysia.net;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.replication.ReplicationTable;
import fr.epistudio.epysia.net.replication.SnapshotPriority;
import fr.epistudio.epysia.net.replication.SnapshotRequest;
import fr.epistudio.epysia.net.replication.SnapshotWriter;
import fr.epistudio.epysia.net.replication.StateCapture;
import fr.epistudio.epysia.net.replication.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SnapshotCeilingTest {
    private static final int OBJECT_COUNT = 40;
    private static final int TIGHT_CEILING = 120;
    private static final int WRITER_CAPACITY = 8192;

    private final ReplicationTable table = ReplicationTable.builder()
            .addComponentType(ReplicatedStats.class)
            .build();
    private final SnapshotWriter snapshotWriter = new SnapshotWriter(table);
    private final StateCapture capture = new StateCapture(table);

    @Test
    void aTightCeilingTruncatesAndReportsWhatItDropped() {
        WorldState current = populatedWorld();
        NetWriter writer = NetWriter.allocate(WRITER_CAPACITY);
        snapshotWriter.write(writer, requestWith(current, TIGHT_CEILING, SnapshotPriority.NONE));
        int written = snapshotWriter.writtenNetworkIds().size();
        assertTrue(written < OBJECT_COUNT, "the ceiling should have cut the snapshot short");
        assertEquals(OBJECT_COUNT - written, snapshotWriter.droppedObjects());
    }

    @Test
    void anUnboundedSnapshotCarriesEveryObject() {
        WorldState current = populatedWorld();
        NetWriter writer = NetWriter.allocate(WRITER_CAPACITY);
        snapshotWriter.write(writer, requestWith(current, SnapshotRequest.NO_CEILING, SnapshotPriority.NONE));
        assertEquals(OBJECT_COUNT, snapshotWriter.writtenNetworkIds().size());
        assertEquals(0, snapshotWriter.droppedObjects());
    }

    @Test
    void priorityDecidesWhoSurvivesTheCeiling() {
        WorldState current = populatedWorld();
        NetWriter writer = NetWriter.allocate(WRITER_CAPACITY);
        SnapshotPriority farthestLast = networkId -> OBJECT_COUNT - networkId;
        snapshotWriter.write(writer, requestWith(current, TIGHT_CEILING, farthestLast));
        List<Integer> written = new ArrayList<>(snapshotWriter.writtenNetworkIds());
        assertTrue(written.contains(OBJECT_COUNT), "the highest priority object must survive");
        assertTrue(written.size() < OBJECT_COUNT, "the ceiling should still have cut the snapshot short");
    }

    private WorldState populatedWorld() {
        WorldState current = new WorldState();
        for (int networkId = 1; networkId <= OBJECT_COUNT; networkId++) {
            GameObject holder = new GameObject("object" + networkId);
            holder.addComponent(new ReplicatedStats().setHealth(networkId).setDisplayName("name" + networkId));
            capture.capture(holder, networkId, current);
        }
        return current;
    }

    private static SnapshotRequest requestWith(WorldState current, int ceiling, SnapshotPriority priority) {
        Map<Integer, Integer> owners = new HashMap<>();
        for (int networkId = 1; networkId <= OBJECT_COUNT; networkId++) {
            owners.put(networkId, 1);
        }
        return new SnapshotRequest(current, new WorldState(), owners, 1, 5,
                SnapshotRequest.NO_BASELINE, ceiling, priority);
    }
}
