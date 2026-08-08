package fr.epistudio.epysia.net.replication;

import java.util.HashMap;
import java.util.Map;

public final class FieldSendGate {
    public static final FieldSendGate ALWAYS_OPEN = new FieldSendGate();

    private static final int COMPONENT_SHIFT = 16;
    private static final int OBJECT_SHIFT = 32;

    private final Map<Long, Integer> lastSentTickByField = new HashMap<>();
    private final boolean throttling;

    private FieldSendGate() {
        this.throttling = false;
    }

    public FieldSendGate(boolean throttling) {
        this.throttling = throttling;
    }

    public static FieldSendGate throttled() {
        return new FieldSendGate(true);
    }

    public boolean allows(int networkId, int componentIndex, int fieldIndex, int intervalTicks, int currentTick) {
        if (!throttling || intervalTicks <= 1) {
            return true;
        }
        Integer lastSent = lastSentTickByField.get(keyOf(networkId, componentIndex, fieldIndex));
        return lastSent == null || currentTick - lastSent >= intervalTicks;
    }

    public void markSent(int networkId, int componentIndex, int fieldIndex, int currentTick) {
        if (!throttling) {
            return;
        }
        lastSentTickByField.put(keyOf(networkId, componentIndex, fieldIndex), currentTick);
    }

    public void forget(int networkId) {
        if (!throttling) {
            return;
        }
        lastSentTickByField.keySet().removeIf(key -> (int) (key >>> OBJECT_SHIFT) == networkId);
    }

    public void clear() {
        lastSentTickByField.clear();
    }

    private static long keyOf(int networkId, int componentIndex, int fieldIndex) {
        return ((long) networkId << OBJECT_SHIFT)
                | ((long) componentIndex << COMPONENT_SHIFT)
                | (fieldIndex & 0xFFFFL);
    }
}
