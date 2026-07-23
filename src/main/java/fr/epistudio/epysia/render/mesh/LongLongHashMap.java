package fr.epistudio.epysia.render.mesh;

import java.util.Arrays;
import java.util.function.LongPredicate;

final class LongLongHashMap {

    private static final long EMPTY = 0L;

    private long[] keys;
    private long[] values;
    private int size;
    private boolean zeroKeyPresent;
    private long zeroKeyValue;

    LongLongHashMap(int expectedEntries) {
        int capacity = tableCapacityFor(expectedEntries);
        keys = new long[capacity];
        values = new long[capacity];
    }

    private static int tableCapacityFor(int expectedEntries) {
        int minimum = Math.max(16, expectedEntries * 2);
        int capacity = 1;
        while (capacity < minimum) {
            capacity <<= 1;
        }
        return capacity;
    }

    void put(long key, long value) {
        if (key == EMPTY) {
            zeroKeyPresent = true;
            zeroKeyValue = value;
            return;
        }
        growIfCrowded();
        int slot = slotOf(key);
        if (keys[slot] == EMPTY) {
            keys[slot] = key;
            size++;
        }
        values[slot] = value;
    }

    long getOrDefault(long key, long fallback) {
        if (key == EMPTY) {
            return zeroKeyPresent ? zeroKeyValue : fallback;
        }
        int slot = slotOf(key);
        return keys[slot] == EMPTY ? fallback : values[slot];
    }

    boolean containsKey(long key) {
        if (key == EMPTY) {
            return zeroKeyPresent;
        }
        return keys[slotOf(key)] != EMPTY;
    }

    boolean containsEntry(long key, long value) {
        if (key == EMPTY) {
            return zeroKeyPresent && zeroKeyValue == value;
        }
        int slot = slotOf(key);
        return keys[slot] != EMPTY && values[slot] == value;
    }

    boolean allKeysMatch(LongPredicate predicate) {
        if (zeroKeyPresent && !predicate.test(EMPTY)) {
            return false;
        }
        for (int slot = 0; slot < keys.length; slot++) {
            if (keys[slot] != EMPTY && !predicate.test(keys[slot])) {
                return false;
            }
        }
        return true;
    }

    void clear() {
        Arrays.fill(keys, EMPTY);
        size = 0;
        zeroKeyPresent = false;
    }

    int size() {
        return zeroKeyPresent ? size + 1 : size;
    }

    private int slotOf(long key) {
        int mask = keys.length - 1;
        int slot = spread(key) & mask;
        while (keys[slot] != EMPTY && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static int spread(long key) {
        long hash = key * 0x9E3779B97F4A7C15L;
        return (int) (hash ^ (hash >>> 32));
    }

    private void growIfCrowded() {
        if ((size + 1) * 2 <= keys.length) {
            return;
        }
        long[] previousKeys = keys;
        long[] previousValues = values;
        keys = new long[previousKeys.length * 2];
        values = new long[previousKeys.length * 2];
        size = 0;
        reinsert(previousKeys, previousValues);
    }

    private void reinsert(long[] previousKeys, long[] previousValues) {
        for (int slot = 0; slot < previousKeys.length; slot++) {
            if (previousKeys[slot] != EMPTY) {
                int destination = slotOf(previousKeys[slot]);
                keys[destination] = previousKeys[slot];
                values[destination] = previousValues[slot];
                size++;
            }
        }
    }
}
