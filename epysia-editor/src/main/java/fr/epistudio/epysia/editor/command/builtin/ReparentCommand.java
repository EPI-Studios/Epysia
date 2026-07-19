package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class ReparentCommand implements EditorCommand {

    private final GameObject child;
    private final Optional<GameObject> newParent;

    public ReparentCommand(GameObject child, Optional<GameObject> newParent) {
        this.child = child;
        this.newParent = newParent;
    }

    @Override
    public void apply(CommandContext context) {
        Transform3D childTransform = transformOf(child);
        Optional<Transform3D> parentTransform = newParent.map(ReparentCommand::transformOf);
        Matrix4f worldBefore = new Matrix4f(childTransform.worldMatrix());
        boolean accepted = childTransform.setParent(parentTransform.orElse(null));
        if (!accepted) {
            throw new IllegalStateException("Reparenting would create a cycle");
        }
        applyLocalFromWorld(childTransform, parentTransform, worldBefore);
    }

    private static void applyLocalFromWorld(Transform3D childTransform,
                                            Optional<Transform3D> parentTransform, Matrix4f world) {
        Matrix4f local = parentTransform
                .map(parent -> new Matrix4f(parent.worldMatrix()).invert().mul(world))
                .orElse(world);
        Vector3f position = local.getTranslation(new Vector3f());
        Quaternionf rotation = local.getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f scale = local.getScale(new Vector3f());
        childTransform.setPosition(position.x, position.y, position.z);
        childTransform.setRotation(rotation);
        childTransform.setScale(scale.x, scale.y, scale.z);
        childTransform.markDirty();
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        Optional<GameObject> currentParent = transformOf(child).parent().flatMap(Transform3D::owner);
        return new ReparentCommand(child, currentParent);
    }

    @Override
    public String label() {
        return "Reparent " + child.name();
    }

    private static Transform3D transformOf(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new IllegalStateException(gameObject.name() + " has no Transform3D"));
    }
}
