package fr.epistudio.epysia.editor.commands;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public record EditorCommand(String id, Supplier<String> group, Supplier<String> label,
                            String shortcut, BooleanSupplier available, Runnable action) {

    private static final String GROUP_SEPARATOR = " / ";

    public static EditorCommand of(String id, Supplier<String> group, Supplier<String> label,
                                   Runnable action) {
        return new EditorCommand(id, group, label, "", () -> true, action);
    }

    public EditorCommand withShortcut(String value) {
        return new EditorCommand(id, group, label, value, available, action);
    }

    public EditorCommand availableWhen(BooleanSupplier condition) {
        return new EditorCommand(id, group, label, shortcut, condition, action);
    }

    public String searchLabel() {
        return group.get() + GROUP_SEPARATOR + label.get();
    }

    public boolean isAvailable() {
        return available.getAsBoolean();
    }

    public void run() {
        action.run();
    }
}
