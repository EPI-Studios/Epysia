package fr.epistudio.epysia.editor.command;

public interface EditorCommand {

    void apply(CommandContext context);

    EditorCommand invert(CommandContext context);

    default String coalesceKey() {
        return null;
    }

    default String label() {
        return getClass().getSimpleName();
    }
}
