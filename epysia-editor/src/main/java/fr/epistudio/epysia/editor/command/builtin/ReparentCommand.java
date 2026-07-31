package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix3x2f;
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
        if (child.getComponent(Transform3D.class).isPresent()) {
            applySpatial();
            return;
        }
        applyPlanar();
    }

    private void applySpatial() {
        Transform3D childTransform = spatialOf(child);
        Optional<Transform3D> parentTransform = newParent.map(ReparentCommand::spatialOf);
        Matrix4f worldBefore = new Matrix4f(childTransform.worldMatrix());
        if (!childTransform.setParent(parentTransform.orElse(null))) {
            throw new IllegalStateException("Reparenting would create a cycle");
        }
        applyLocalFromWorld(childTransform, parentTransform, worldBefore);
    }

    private void applyPlanar() {
        Transform2D childTransform = planarOf(child);
        Optional<Transform2D> parentTransform = newParent.map(ReparentCommand::planarOf);
        Matrix3x2f worldBefore = new Matrix3x2f(childTransform.worldMatrix());
        if (!childTransform.setParent(parentTransform.orElse(null))) {
            throw new IllegalStateException("Reparenting would create a cycle");
        }
        childTransform.setLocalMatrix(parentTransform
                .map(parent -> new Matrix3x2f(parent.worldMatrix()).invert().mul(worldBefore))
                .orElse(worldBefore));
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
        return new ReparentCommand(child, currentParentOf(child));
    }

    private static Optional<GameObject> currentParentOf(GameObject gameObject) {
        Optional<Transform3D> spatial = gameObject.getComponent(Transform3D.class);
        if (spatial.isPresent()) {
            return spatial.get().parent().flatMap(Transform3D::owner);
        }
        return gameObject.getComponent(Transform2D.class)
                .flatMap(Transform2D::parent).flatMap(Transform2D::owner);
    }

    @Override
    public String label() {
        return "Reparent " + child.name();
    }

    private static Transform3D spatialOf(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new IllegalStateException(gameObject.name() + " has no Transform3D"));
    }

    private static Transform2D planarOf(GameObject gameObject) {
        return gameObject.getComponent(Transform2D.class)
                .orElseThrow(() -> new IllegalStateException(gameObject.name() + " has no Transform2D"));
    }
}
