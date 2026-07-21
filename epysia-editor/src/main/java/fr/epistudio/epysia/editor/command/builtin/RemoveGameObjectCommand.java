package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.List;

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
        List<GameObject> subtree = context.scene().subtreeOf(gameObject);
        return new AddSubtreeCommand(gameObject, subtree);
    }

    @Override
    public String label() {
        return "Remove " + gameObject.name();
    }

    private static final class AddSubtreeCommand implements EditorCommand {

        private final GameObject root;
        private final List<GameObject> subtree;

        AddSubtreeCommand(GameObject root, List<GameObject> subtree) {
            this.root = root;
            this.subtree = subtree;
        }

        @Override
        public void apply(CommandContext context) {
            for (GameObject member : subtree) {
                context.scene().addGameObject(member);
            }
            context.scene().advanceTick();
        }

        @Override
        public EditorCommand invert(CommandContext context) {
            return new RemoveGameObjectCommand(root);
        }

        @Override
        public String label() {
            return "Add " + root.name();
        }
    }
}
