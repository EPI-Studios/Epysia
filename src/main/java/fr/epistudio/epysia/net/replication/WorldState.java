package fr.epistudio.epysia.net.replication;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorldState {
    public static final Object ABSENT = new Object();

    private final Map<Integer, ObjectState> objectsByNetworkId = new LinkedHashMap<>();

    public ObjectState objectFor(int networkId) {
        return objectsByNetworkId.computeIfAbsent(networkId, ignored -> new ObjectState());
    }

    public Optional<ObjectState> find(int networkId) {
        return Optional.ofNullable(objectsByNetworkId.get(networkId));
    }

    public Set<Integer> networkIds() {
        return Set.copyOf(objectsByNetworkId.keySet());
    }

    public Set<Integer> networkIdView() {
        return objectsByNetworkId.keySet();
    }

    public void retainOnly(Set<Integer> liveNetworkIds) {
        objectsByNetworkId.keySet().retainAll(liveNetworkIds);
    }

    public boolean contains(int networkId) {
        return objectsByNetworkId.containsKey(networkId);
    }

    public void remove(int networkId) {
        objectsByNetworkId.remove(networkId);
    }

    public void clear() {
        objectsByNetworkId.clear();
    }

    public void replaceWith(WorldState source) {
        objectsByNetworkId.clear();
        for (Map.Entry<Integer, ObjectState> entry : source.objectsByNetworkId.entrySet()) {
            objectsByNetworkId.put(entry.getKey(), entry.getValue().copy());
        }
    }

    public int objectCount() {
        return objectsByNetworkId.size();
    }

    public WorldState copy() {
        WorldState duplicate = new WorldState();
        for (Map.Entry<Integer, ObjectState> entry : objectsByNetworkId.entrySet()) {
            duplicate.objectsByNetworkId.put(entry.getKey(), entry.getValue().copy());
        }
        return duplicate;
    }

    public WorldState shallowCopy() {
        WorldState duplicate = new WorldState();
        duplicate.objectsByNetworkId.putAll(objectsByNetworkId);
        return duplicate;
    }

    public ObjectState detach(int networkId) {
        ObjectState shared = objectsByNetworkId.get(networkId);
        ObjectState owned = shared == null ? new ObjectState() : shared.copy();
        objectsByNetworkId.put(networkId, owned);
        return owned;
    }

    public static final class ObjectState {
        private final Map<Integer, ComponentState> byComponentIndex = new LinkedHashMap<>();

        public static ObjectState empty() {
            return new ObjectState();
        }

        public ComponentState componentFor(int componentIndex, int fieldCount) {
            return byComponentIndex.computeIfAbsent(componentIndex,
                    ignored -> ComponentState.ofFields(fieldCount));
        }

        public void put(int componentIndex, ComponentState state) {
            byComponentIndex.put(componentIndex, state);
        }

        public Optional<ComponentState> find(int componentIndex) {
            return Optional.ofNullable(byComponentIndex.get(componentIndex));
        }

        public Set<Integer> componentIndices() {
            return byComponentIndex.keySet();
        }

        public ObjectState copy() {
            ObjectState duplicate = new ObjectState();
            for (Map.Entry<Integer, ComponentState> entry : byComponentIndex.entrySet()) {
                duplicate.byComponentIndex.put(entry.getKey(), entry.getValue().copy());
            }
            return duplicate;
        }
    }

    public static final class ComponentState {
        private static final byte[] NO_PAYLOAD = new byte[0];

        private final Object[] values;
        private byte[] customPayload;

        private ComponentState(Object[] values, byte[] customPayload) {
            this.values = values;
            this.customPayload = customPayload;
        }

        public static ComponentState ofFields(int fieldCount) {
            Object[] values = new Object[fieldCount];
            Arrays.fill(values, ABSENT);
            return new ComponentState(values, NO_PAYLOAD);
        }

        public static ComponentState ofCustomPayload(byte[] payload) {
            return new ComponentState(new Object[0], payload);
        }

        public Object valueAt(int fieldIndex) {
            return values[fieldIndex];
        }

        public void setValueAt(int fieldIndex, Object value) {
            values[fieldIndex] = value;
        }

        public int fieldCount() {
            return values.length;
        }

        public byte[] customPayload() {
            return customPayload;
        }

        public void setCustomPayload(byte[] payload) {
            this.customPayload = payload;
        }

        public boolean hasCustomPayload() {
            return customPayload.length > 0;
        }

        public ComponentState copy() {
            return new ComponentState(values.clone(), customPayload.clone());
        }
    }
}
