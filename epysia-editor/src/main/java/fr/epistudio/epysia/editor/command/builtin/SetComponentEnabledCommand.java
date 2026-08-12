package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;

public final class SetComponentEnabledCommand implements EditorCommand {

    private final IComponent target;
    private final boolean previousValue;
    private final boolean nextValue;

    public SetComponentEnabledCommand(IComponent target, boolean previousValue, boolean nextValue) {
        this.target = target;
        this.previousValue = previousValue;
        this.nextValue = nextValue;
    }

    @Override
    public void apply(CommandContext context) {
        target.setEnabled(nextValue);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetComponentEnabledCommand(target, nextValue, previousValue);
    }

    @Override
    public String label() {
        return (nextValue ? "Enable " : "Disable ") + target.getClass().getSimpleName();
    }
}
