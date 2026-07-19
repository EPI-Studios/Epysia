package fr.epistudio.epysia.editor.command;

import java.util.ArrayList;
import java.util.List;

public final class CompositeCommand implements EditorCommand {

    private final String label;
    private final List<EditorCommand> commands;

    public CompositeCommand(String label, List<EditorCommand> commands) {
        this.label = label;
        this.commands = List.copyOf(commands);
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
        for (int i = commands.size() - 1; i >= 0; i--) {
            inverses.add(commands.get(i).invert(context));
        }
        return new CompositeCommand(label, inverses);
    }

    @Override
    public String label() {
        return label;
    }
}
