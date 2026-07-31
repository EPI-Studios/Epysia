package fr.epistudio.epysia.input;

import java.util.ArrayList;
import java.util.List;

public final class InputEventRing {

    public static final int CAPACITY = 256;

    private final InputEvent[] entries = new InputEvent[CAPACITY];
    private int writeCursor;
    private int size;

    public void record(InputEvent event) {
        entries[writeCursor] = event;
        writeCursor = (writeCursor + 1) % CAPACITY;
        size = Math.min(size + 1, CAPACITY);
    }

    public int size() {
        return size;
    }

    public List<InputEvent> recent() {
        List<InputEvent> ordered = new ArrayList<>(size);
        for (int offset = 0; offset < size; offset++) {
            ordered.add(entries[(writeCursor - size + offset + CAPACITY) % CAPACITY]);
        }
        return ordered;
    }

    public void clear() {
        writeCursor = 0;
        size = 0;
    }
}
