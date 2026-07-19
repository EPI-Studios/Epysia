package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class RenameCommand implements EditorCommand {

    private final GameObject target;
    private final String previousName;
    private final String nextName;

    public RenameCommand(GameObject target, String previousName, String nextName) {
        this.target = target;
        this.previousName = previousName;
        this.nextName = nextName;
    }

    @Override
    public void apply(CommandContext context) {
        target.setName(nextName);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RenameCommand(target, nextName, previousName);
    }

    @Override
    public String label() {
        return "Rename " + previousName + " → " + nextName;
    }
}
