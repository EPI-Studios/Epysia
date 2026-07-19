package fr.epistudio.epysia.editor.command;

import java.util.ArrayDeque;
import java.util.Deque;

public final class EditorHistory {

    private static final int MAX_ENTRIES = 200;
    private static final long COALESCE_WINDOW_NANOS = 250_000_000L;

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final CommandContext context;
    private Runnable onChange = () -> {
    };

    public EditorHistory(CommandContext context) {
        this.context = context;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange != null ? onChange : () -> {
        };
    }

    public void execute(EditorCommand command) {
        EditorCommand inverse;
        try {
            inverse = command.invert(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] invert failed for " + command.label(), error);
            return;
        }
        try {
            command.apply(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] apply failed for " + command.label(), error);
            return;
        }
        push(command, inverse);
        onChange.run();
    }

    public void executeWithoutHistory(EditorCommand command) {
        try {
            command.apply(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] apply (no history) failed for " + command.label(), error);
        }
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
        EditorCommand refreshedForward;
        try {
            refreshedForward = entry.inverse.invert(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] invert-before-undo failed for " + entry.forward.label(), error);
            refreshedForward = entry.forward;
        }
        try {
            entry.inverse.apply(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] undo failed for " + entry.forward.label(), error);
            return;
        }
        entry.forward = refreshedForward;
        redoStack.push(entry);
        onChange.run();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Entry entry = redoStack.pop();
        EditorCommand refreshedInverse;
        try {
            refreshedInverse = entry.forward.invert(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] invert-before-redo failed for " + entry.forward.label(), error);
            refreshedInverse = entry.inverse;
        }
        try {
            entry.forward.apply(context);
        } catch (RuntimeException error) {
            context.services().logger().error("[EditorHistory] redo failed for " + entry.forward.label(), error);
            return;
        }
        entry.inverse = refreshedInverse;
        undoStack.push(entry);
        onChange.run();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public java.util.Optional<String> undoLabel() {
        return undoStack.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(undoStack.peek().forward.label());
    }

    public java.util.Optional<String> redoLabel() {
        return redoStack.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(redoStack.peek().forward.label());
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    private static final class Entry {
        EditorCommand forward;
        EditorCommand inverse;
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
