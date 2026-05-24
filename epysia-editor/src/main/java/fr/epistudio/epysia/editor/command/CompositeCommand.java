package fr.epistudio.epysia.editor.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompositeCommand implements EditorCommand {

    private final List<EditorCommand> commands;
    private final String label;
    private final String coalesceKey;

    public CompositeCommand(String label, List<EditorCommand> commands) {
        this(label, commands, null);
    }

    public CompositeCommand(String label, List<EditorCommand> commands, String coalesceKey) {
        this.label = label;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.coalesceKey = coalesceKey;
    }

    @Override
    public void apply(CommandContext context) {
        for (EditorCommand command : commands) {
            command.apply(context);
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        List<EditorCommand> inverses = new ArrayList<>(commands.size());
        for (int index = commands.size() - 1; index >= 0; index--) {
            inverses.add(commands.get(index).invert(context));
        }
        return new CompositeCommand(label, inverses, coalesceKey);
    }

    @Override
    public String coalesceKey() {
        return coalesceKey;
    }

    @Override
    public String label() {
        return label;
    }
}
