package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class AddGameObjectCommand implements EditorCommand {

    private final GameObject gameObject;
    private final boolean selectAfter;

    public AddGameObjectCommand(GameObject gameObject, boolean selectAfter) {
        this.gameObject = gameObject;
        this.selectAfter = selectAfter;
    }

    @Override
    public void apply(CommandContext context) {
        context.scene().addGameObject(gameObject);
        context.scene().advanceTick();
        if (selectAfter) {
            context.selection().select(gameObject);
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RemoveGameObjectCommand(gameObject);
    }

    @Override
    public String label() {
        return "Add " + gameObject.name();
    }
}
