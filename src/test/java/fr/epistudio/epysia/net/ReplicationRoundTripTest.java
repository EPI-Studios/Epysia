package fr.epistudio.epysia.net;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.replication.ReplicationTable;
import fr.epistudio.epysia.net.replication.SnapshotReader;
import fr.epistudio.epysia.net.replication.SnapshotRequest;
import fr.epistudio.epysia.net.replication.SnapshotWriter;
import fr.epistudio.epysia.net.replication.StateApply;
import fr.epistudio.epysia.net.replication.StateCapture;
import fr.epistudio.epysia.net.replication.WorldState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReplicationRoundTripTest {
    private static final int NETWORK_ID = 7;
    private static final int OWNING_PEER = 1;
    private static final int SNAPSHOT_CAPACITY = 4096;

    private final ReplicationTable table = ReplicationTable.builder()
            .addComponentType(ReplicatedStats.class)
            .build();
    private final SnapshotWriter snapshotWriter = new SnapshotWriter(table);
    private final SnapshotReader snapshotReader = new SnapshotReader(table);
    private final StateCapture capture = new StateCapture(table);
    private final StateApply apply = new StateApply(table);
    private final TreeMap<Integer, WorldState> clientHistory = new TreeMap<>();

    private final GameObject serverObject = new GameObject("server");
    private final GameObject clientObject = new GameObject("client");
    private final ReplicatedStats serverStats = serverObject.addComponent(new ReplicatedStats());
    private final ReplicatedStats clientStats = clientObject.addComponent(new ReplicatedStats());

    private WorldState serverBaseline = new WorldState();

    @Test
    void fullSnapshotCarriesEveryField() {
        serverStats.setHealth(72).setDisplayName("meek").setAmmunition(12);
        serverStats.aim().set(1.0f, 2.0f, 3.0f);
        exchange(1, SnapshotRequest.NO_BASELINE);
        assertEquals(72, clientStats.health());
        assertEquals("meek", clientStats.displayName());
        assertEquals(3.0f, clientStats.aim().z);
        assertEquals(12, clientStats.ammunition());
    }

    @Test
    void deltaAgainstBaselineCarriesOnlyTheChangedField() {
        serverStats.setHealth(72).setDisplayName("meek");
        exchange(1, SnapshotRequest.NO_BASELINE);
        serverStats.setHealth(41);
        int deltaBytes = exchange(2, 1);
        assertEquals(41, clientStats.health());
        assertEquals("meek", clientStats.displayName());
        assertTrue(deltaBytes < 24, "a one field delta should stay small but measured " + deltaBytes);
    }

    @Test
    void aDroppedAcknowledgementKeepsTheOlderBaselineUsable() {
        serverStats.setHealth(72);
        exchange(1, SnapshotRequest.NO_BASELINE);
        serverStats.setHealth(50);
        exchange(2, 1);
        serverStats.setHealth(33);
        exchange(3, 1);
        assertEquals(33, clientStats.health());
    }

    @Test
    void ownerOnlyFieldsStayWithTheOwner() {
        serverStats.setAmmunition(5);
        WorldState current = new WorldState();
        capture.capture(serverObject, NETWORK_ID, current);
        NetWriter writer = NetWriter.allocate(SNAPSHOT_CAPACITY);
        snapshotWriter.write(writer, new SnapshotRequest(current, new WorldState(),
                Map.of(NETWORK_ID, OWNING_PEER), OWNING_PEER + 1, 1, SnapshotRequest.NO_BASELINE));
        applyReceived(writer, Set.of());
        assertEquals(30, clientStats.ammunition());
    }

    private int exchange(int serverTick, int baselineTick) {
        WorldState current = new WorldState();
        capture.capture(serverObject, NETWORK_ID, current);
        NetWriter writer = NetWriter.allocate(SNAPSHOT_CAPACITY);
        SnapshotRequest request = new SnapshotRequest(current, serverBaseline,
                Map.of(NETWORK_ID, OWNING_PEER), OWNING_PEER, serverTick, baselineTick);
        WorldState delivered = snapshotWriter.write(writer, request);
        int bytes = writer.position();
        applyReceived(writer, Set.of());
        serverBaseline = delivered;
        return bytes;
    }

    private void applyReceived(NetWriter writer, Set<Class<?>> excluded) {
        NetReader reader = new NetReader(writer.flipped());
        Optional<SnapshotReader.ReadResult> result = snapshotReader.read(reader,
                tick -> Optional.ofNullable(clientHistory.get(tick)));
        assertTrue(result.isPresent(), "the client should accept a snapshot whose baseline it still holds");
        clientHistory.put(result.get().serverTick(), result.get().state().copy());
        result.get().state().find(NETWORK_ID)
                .ifPresent(state -> apply.apply(clientObject, state, excluded));
    }
}
