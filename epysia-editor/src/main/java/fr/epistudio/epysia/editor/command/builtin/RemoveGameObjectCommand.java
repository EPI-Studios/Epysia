package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class RemoveGameObjectCommand implements EditorCommand {

    private final GameObject gameObject;

    public RemoveGameObjectCommand(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    @Override
    public void apply(CommandContext context) {
        context.scene().removeGameObject(gameObject);
        context.scene().advanceTick();
        context.selection().deselect(gameObject);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new AddGameObjectCommand(gameObject, false);
    }

    @Override
    public String label() {
        return "Remove " + gameObject.name();
    }
}
