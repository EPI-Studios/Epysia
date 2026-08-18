package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.ArrayList;
import java.util.List;

public final class AddGameObjectCommand implements EditorCommand {

    private final GameObject gameObject;
    private final boolean selectAfter;

    public AddGameObjectCommand(GameObject gameObject, boolean selectAfter) {
        this.gameObject = gameObject;
        this.selectAfter = selectAfter;
    }

    @Override
    public void apply(CommandContext context) {
        for (GameObject member : subtreeOf(gameObject)) {
            context.scene().addGameObject(member);
            for (IComponent component : member.components()) {
                component.onLoad(context.services());
            }
        }
        context.scene().advanceTick();
        if (selectAfter) {
            context.selection().select(gameObject);
        }
    }

    private static List<GameObject> subtreeOf(GameObject root) {
        List<GameObject> collected = new ArrayList<>();
        collect(root, collected);
        return collected;
    }

    private static void collect(GameObject current, List<GameObject> collected) {
        collected.add(current);
        for (GameObject child : current.children()) {
            collect(child, collected);
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
