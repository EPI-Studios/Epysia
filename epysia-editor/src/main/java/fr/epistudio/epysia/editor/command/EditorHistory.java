package fr.epistudio.epysia.editor.command;

import java.util.ArrayDeque;
import java.util.Deque;

public final class EditorHistory {

    private static final int MAX_ENTRIES = 200;
    private static final long COALESCE_WINDOW_NANOS = 250_000_000L;

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final CommandContext context;

    public EditorHistory(CommandContext context) {
        this.context = context;
    }

    public void execute(EditorCommand command) {
        EditorCommand inverse = command.invert(context);
        command.apply(context);
        push(command, inverse);
    }

    public void executeWithoutHistory(EditorCommand command) {
        command.apply(context);
    }

    private void push(EditorCommand forward, EditorCommand inverse) {
        long now = System.nanoTime();
        String key = forward.coalesceKey();
        if (key != null && !undoStack.isEmpty()) {
            Entry top = undoStack.peek();
            if (key.equals(top.coalesceKey) && (now - top.timestampNanos) < COALESCE_WINDOW_NANOS) {
                top.forward = forward;
                top.timestampNanos = now;
                redoStack.clear();
                return;
            }
        }
        undoStack.push(new Entry(forward, inverse, key, now));
        redoStack.clear();
        while (undoStack.size() > MAX_ENTRIES) {
            undoStack.pollLast();
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Entry entry = undoStack.pop();
        entry.inverse.apply(context);
        redoStack.push(entry);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Entry entry = redoStack.pop();
        entry.forward.apply(context);
        undoStack.push(entry);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    public String peekUndoLabel() {
        return undoStack.isEmpty() ? null : undoStack.peek().forward.label();
    }

    public String peekRedoLabel() {
        return redoStack.isEmpty() ? null : redoStack.peek().forward.label();
    }

    private static final class Entry {
        EditorCommand forward;
        final EditorCommand inverse;
        final String coalesceKey;
        long timestampNanos;

        Entry(EditorCommand forward, EditorCommand inverse, String coalesceKey, long timestampNanos) {
            this.forward = forward;
            this.inverse = inverse;
            this.coalesceKey = coalesceKey;
            this.timestampNanos = timestampNanos;
        }
    }
}
