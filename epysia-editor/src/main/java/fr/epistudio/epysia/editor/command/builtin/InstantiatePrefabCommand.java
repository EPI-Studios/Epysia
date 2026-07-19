package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InstantiatePrefabCommand implements EditorCommand {

    private final Path prefabPath;
    private final Vector3f spawnPosition;
    private final List<GameObject> instantiated = new ArrayList<>();

    public InstantiatePrefabCommand(Path prefabPath, Vector3f spawnPosition) {
        this.prefabPath = prefabPath;
        this.spawnPosition = new Vector3f(spawnPosition);
    }

    @Override
    public void apply(CommandContext context) {
        if (instantiated.isEmpty()) {
            instantiateFresh(context);
        } else {
            for (GameObject gameObject : instantiated) {
                context.scene().addGameObject(gameObject);
            }
            context.scene().advanceTick();
        }
        context.selection().select(instantiated.get(0));
    }

    private void instantiateFresh(CommandContext context) {
        PrefabInstantiator instantiator = new PrefabInstantiator(context.componentRegistry());
        GameObject root;
        try {
            root = instantiator.instantiate(prefabPath, context.scene(), context.services());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        collectSubtree(root, instantiated);
        root.getComponent(Transform3D.class).ifPresent(transform ->
                transform.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z));
    }

    private static void collectSubtree(GameObject current, List<GameObject> out) {
        out.add(current);
        current.getComponent(Transform3D.class).ifPresent(transform -> {
            for (Transform3D child : transform.children()) {
                child.owner().ifPresent(owner -> collectSubtree(owner, out));
            }
        });
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new RemoveInstancesCommand(this);
    }

    @Override
    public String label() {
        return "Instantiate " + prefabPath.getFileName();
    }

    private static final class RemoveInstancesCommand implements EditorCommand {

        private final InstantiatePrefabCommand origin;

        RemoveInstancesCommand(InstantiatePrefabCommand origin) {
            this.origin = origin;
        }

        @Override
        public void apply(CommandContext context) {
            for (GameObject gameObject : origin.instantiated) {
                context.scene().removeGameObject(gameObject);
                context.selection().deselect(gameObject);
            }
            context.scene().advanceTick();
        }

        @Override
        public EditorCommand invert(CommandContext context) {
            return origin;
        }

        @Override
        public String label() {
            return "Remove " + origin.prefabPath.getFileName() + " instance";
        }
    }
}
