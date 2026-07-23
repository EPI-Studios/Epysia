package fr.epistudio.epysia.render.mesh;

final class LongHashSet {

    private static final long PRESENT = 1L;

    private final LongLongHashMap entries;

    LongHashSet(int expectedEntries) {
        entries = new LongLongHashMap(expectedEntries);
    }

    void add(long value) {
        entries.put(value, PRESENT);
    }

    boolean contains(long value) {
        return entries.containsKey(value);
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }
}
