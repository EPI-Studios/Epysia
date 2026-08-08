package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.net.protocol.NetWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SnapshotWriter {
    private final ReplicationTable table;
    private final List<Integer> writtenNetworkIds = new ArrayList<>();
    private Map<Integer, List<ComponentDelta>> pendingChanges = Map.of();
    private int droppedObjects;
    private int culledObjects;

    public SnapshotWriter(ReplicationTable table) {
        this.table = table;
    }

    public WorldState write(NetWriter writer, SnapshotRequest request) {
        WorldState delivered = request.baseline().shallowCopy();
        List<Integer> ordered = orderByPriority(request, collectChanges(request));
        writer.writeInt(request.serverTick());
        writer.writeInt(request.baselineTick());
        int countPosition = writer.position();
        writer.writeShort(0);
        int written = writeWithinCeiling(writer, request, delivered, ordered);
        writer.patchShort(countPosition, written);
        this.droppedObjects = ordered.size() - written;
        dropObjectsNoLongerPresent(request, delivered);
        return delivered;
    }

    public int droppedObjects() {
        return droppedObjects;
    }

    public int culledObjects() {
        return culledObjects;
    }

    public List<Integer> writtenNetworkIds() {
        return writtenNetworkIds;
    }

    private int writeWithinCeiling(NetWriter writer, SnapshotRequest request, WorldState delivered,
                                   List<Integer> ordered) {
        writtenNetworkIds.clear();
        for (int networkId : ordered) {
            if (!writtenNetworkIds.isEmpty() && writer.position() >= request.byteCeiling()) {
                break;
            }
            writeObject(writer, request, delivered, networkId, pendingChanges.get(networkId));
            writtenNetworkIds.add(networkId);
        }
        return writtenNetworkIds.size();
    }

    private List<Integer> orderByPriority(SnapshotRequest request, Map<Integer, List<ComponentDelta>> changes) {
        pendingChanges = changes;
        List<Integer> ordered = new ArrayList<>(changes.keySet());
        if (request.priority() != SnapshotPriority.NONE) {
            ordered.sort(Comparator.comparingDouble(networkId -> request.priority().scoreOf(networkId)));
        }
        return ordered;
    }

    private static void dropObjectsNoLongerPresent(SnapshotRequest request, WorldState delivered) {
        for (int networkId : List.copyOf(delivered.networkIds())) {
            if (!request.current().contains(networkId)) {
                delivered.remove(networkId);
            }
        }
    }

    private Map<Integer, List<ComponentDelta>> collectChanges(SnapshotRequest request) {
        Map<Integer, List<ComponentDelta>> changes = new LinkedHashMap<>();
        Collection<Integer> relevant = request.interest().relevantNetworkIds(request.current());
        culledObjects = request.current().objectCount() - relevant.size();
        for (int networkId : relevant) {
            if (!request.current().contains(networkId)) {
                continue;
            }
            List<ComponentDelta> deltas = componentDeltasFor(request, networkId);
            if (!deltas.isEmpty()) {
                changes.put(networkId, deltas);
            }
        }
        return changes;
    }

    private List<ComponentDelta> componentDeltasFor(SnapshotRequest request, int networkId) {
        WorldState.ObjectState current = request.current().find(networkId).orElseGet(WorldState.ObjectState::empty);
        WorldState.ObjectState baseline = request.baseline().find(networkId)
                .orElseGet(WorldState.ObjectState::empty);
        List<ComponentDelta> deltas = new ArrayList<>();
        for (int componentIndex : current.componentIndices()) {
            componentDelta(request, current, baseline, componentIndex, networkId).ifPresent(deltas::add);
        }
        return deltas;
    }

    private Optional<ComponentDelta> componentDelta(SnapshotRequest request, WorldState.ObjectState current,
                                                    WorldState.ObjectState baseline, int componentIndex,
                                                    int networkId) {
        WorldState.ComponentState currentState = current.find(componentIndex).orElseThrow();
        Optional<WorldState.ComponentState> baselineState = baseline.find(componentIndex);
        if (currentState.hasCustomPayload()) {
            return customDelta(currentState, baselineState, componentIndex);
        }
        List<Integer> changedFields = changedFieldIndices(request, currentState, baselineState,
                componentIndex, networkId);
        if (changedFields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ComponentDelta(componentIndex, changedFields, currentState));
    }

    private static Optional<ComponentDelta> customDelta(WorldState.ComponentState currentState,
                                                        Optional<WorldState.ComponentState> baselineState,
                                                        int componentIndex) {
        boolean unchanged = baselineState
                .map(previous -> Arrays.equals(previous.customPayload(), currentState.customPayload()))
                .orElse(false);
        if (unchanged) {
            return Optional.empty();
        }
        return Optional.of(new ComponentDelta(componentIndex, List.of(), currentState));
    }

    private List<Integer> changedFieldIndices(SnapshotRequest request, WorldState.ComponentState currentState,
                                              Optional<WorldState.ComponentState> baselineState,
                                              int componentIndex, int networkId) {
        List<ReplicatedField> fields = fieldsAt(componentIndex);
        List<Integer> changed = new ArrayList<>();
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
            if (isFieldChanged(request, currentState, baselineState, fields.get(fieldIndex),
                    componentIndex, fieldIndex, networkId)) {
                changed.add(fieldIndex);
            }
        }
        return changed;
    }

    private boolean isFieldChanged(SnapshotRequest request, WorldState.ComponentState currentState,
                                   Optional<WorldState.ComponentState> baselineState, ReplicatedField field,
                                   int componentIndex, int fieldIndex, int networkId) {
        if (field.condition() == ReplicationCondition.OWNER_ONLY && !request.targetOwns(networkId)) {
            return false;
        }
        if (!request.sendGate().allows(networkId, componentIndex, fieldIndex,
                field.sendIntervalTicks(request.tickRate()), request.serverTick())) {
            return false;
        }
        Object currentValue = currentState.valueAt(fieldIndex);
        if (currentValue == WorldState.ABSENT) {
            return false;
        }
        Object baselineValue = baselineState
                .map(previous -> previous.valueAt(fieldIndex))
                .orElse(WorldState.ABSENT);
        return baselineValue == WorldState.ABSENT || !field.valuesEqual(currentValue, baselineValue);
    }

    private void writeObject(NetWriter writer, SnapshotRequest request, WorldState delivered,
                             int networkId, List<ComponentDelta> deltas) {
        writer.writeVarInt(networkId);
        writer.writeVarInt(deltas.size());
        WorldState.ObjectState deliveredObject = delivered.detach(networkId);
        for (ComponentDelta delta : deltas) {
            writeComponent(writer, request, deliveredObject, networkId, delta);
        }
    }

    private void writeComponent(NetWriter writer, SnapshotRequest request,
                                WorldState.ObjectState deliveredObject, int networkId, ComponentDelta delta) {
        writer.writeVarInt(delta.componentIndex());
        WorldState.ComponentState source = delta.state();
        if (source.hasCustomPayload()) {
            writer.writeBoolean(true);
            writer.writeSizedBytes(source.customPayload(), 0, source.customPayload().length);
            deliveredObject.put(delta.componentIndex(),
                    WorldState.ComponentState.ofCustomPayload(source.customPayload().clone()));
            return;
        }
        writer.writeBoolean(false);
        writeFields(writer, request, deliveredObject, networkId, delta);
    }

    private void writeFields(NetWriter writer, SnapshotRequest request, WorldState.ObjectState deliveredObject,
                             int networkId, ComponentDelta delta) {
        List<ReplicatedField> fields = fieldsAt(delta.componentIndex());
        WorldState.ComponentState deliveredState =
                deliveredObject.componentFor(delta.componentIndex(), fields.size());
        writer.writeVarInt(delta.changedFieldIndices().size());
        for (int fieldIndex : delta.changedFieldIndices()) {
            writer.writeVarInt(fieldIndex);
            Object value = delta.state().valueAt(fieldIndex);
            fields.get(fieldIndex).writeValue(writer, value);
            deliveredState.setValueAt(fieldIndex, value);
            request.sendGate().markSent(networkId, delta.componentIndex(), fieldIndex, request.serverTick());
        }
    }

    private List<ReplicatedField> fieldsAt(int componentIndex) {
        Class<? extends IComponent> type = table.componentTypes().get(componentIndex);
        return table.fieldsFor(type);
    }

    private record ComponentDelta(int componentIndex, List<Integer> changedFieldIndices,
                                  WorldState.ComponentState state) {
    }
}
