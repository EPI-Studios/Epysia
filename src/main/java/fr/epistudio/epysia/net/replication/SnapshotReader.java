package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.net.protocol.MalformedPacketException;
import fr.epistudio.epysia.net.protocol.NetReader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SnapshotReader {
    @FunctionalInterface
    public interface BaselineLookup {
        Optional<WorldState> stateAt(int tick);
    }

    public record ReadResult(int serverTick, int baselineTick, WorldState state, Set<Integer> touchedNetworkIds) {
    }

    private final ReplicationTable table;

    public SnapshotReader(ReplicationTable table) {
        this.table = table;
    }

    public Optional<ReadResult> read(NetReader reader, BaselineLookup lookup) {
        int serverTick = reader.readInt();
        int baselineTick = reader.readInt();
        Optional<WorldState> baseline = baselineTick == SnapshotRequest.NO_BASELINE
                ? Optional.of(new WorldState())
                : lookup.stateAt(baselineTick);
        if (baseline.isEmpty()) {
            return Optional.empty();
        }
        WorldState merged = baseline.get().shallowCopy();
        Set<Integer> touched = readObjects(reader, merged);
        return Optional.of(new ReadResult(serverTick, baselineTick, merged, touched));
    }

    private Set<Integer> readObjects(NetReader reader, WorldState state) {
        Set<Integer> touched = new LinkedHashSet<>();
        int objectCount = reader.requireCount(reader.readShort(), 2);
        for (int index = 0; index < objectCount; index++) {
            int networkId = reader.readVarInt();
            readComponents(reader, state.detach(networkId));
            touched.add(networkId);
        }
        return touched;
    }

    private void readComponents(NetReader reader, WorldState.ObjectState objectState) {
        int componentCount = reader.requireCount(reader.readVarInt(), 2);
        for (int index = 0; index < componentCount; index++) {
            int componentIndex = reader.readVarInt();
            requireKnownComponentIndex(componentIndex);
            if (reader.readBoolean()) {
                objectState.put(componentIndex, WorldState.ComponentState.ofCustomPayload(reader.readSizedBytes()));
                continue;
            }
            readFields(reader, objectState, componentIndex);
        }
    }

    private void readFields(NetReader reader, WorldState.ObjectState objectState, int componentIndex) {
        List<ReplicatedField> fields = fieldsAt(componentIndex);
        WorldState.ComponentState state = objectState.componentFor(componentIndex, fields.size());
        int changedCount = reader.requireCount(reader.readVarInt(), 2);
        for (int index = 0; index < changedCount; index++) {
            int fieldIndex = reader.readVarInt();
            if (fieldIndex < 0 || fieldIndex >= fields.size()) {
                throw new MalformedPacketException("Snapshot names field " + fieldIndex
                        + " on a component that declares " + fields.size());
            }
            state.setValueAt(fieldIndex, fields.get(fieldIndex).readValue(reader));
        }
    }

    private void requireKnownComponentIndex(int componentIndex) {
        if (componentIndex < 0 || componentIndex >= table.componentTypes().size()) {
            throw new MalformedPacketException("Snapshot names component index " + componentIndex
                    + " outside a table of " + table.componentTypes().size());
        }
    }

    private List<ReplicatedField> fieldsAt(int componentIndex) {
        Class<? extends IComponent> type = table.componentTypes().get(componentIndex);
        return table.fieldsFor(type);
    }
}
