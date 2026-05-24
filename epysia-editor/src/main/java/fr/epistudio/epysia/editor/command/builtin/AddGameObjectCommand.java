package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

public final class AddGameObjectCommand implements EditorCommand {

    private final GameObject gameObject;

    public AddGameObjectCommand(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    @Override
    public void apply(CommandContext context) {
        context.world().scene().addGameObject(gameObject);
        context.world().scene().advanceTick();
        context.selection().setSingle(gameObject);
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
