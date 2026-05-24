package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class RenameCommand implements EditorCommand {

    private final GameObject target;
    private final String newName;

    public RenameCommand(GameObject target, String newName) {
        this.target = target;
        this.newName = newName;
    }

    @Override
    public void apply(CommandContext context) {
        target.setName(newName);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RenameCommand(target, target.name());
    }

    @Override
    public String label() {
        return "Rename to " + newName;
    }
}
