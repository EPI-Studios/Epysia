package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TransformCommand implements EditorCommand {

    private final GameObject target;
    private final Vector3f position;
    private final Quaternionf rotation;
    private final Vector3f scale;
    private final boolean coalescing;

    public TransformCommand(GameObject target, Vector3f position, Quaternionf rotation, Vector3f scale, boolean coalescing) {
        this.target = target;
        this.position = new Vector3f(position);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
        this.coalescing = coalescing;
    }

    public static TransformCommand snapshot(GameObject target) {
        Transform3D transform = transformOf(target);
        return new TransformCommand(target, transform.position(), transform.rotation(), transform.scale(), false);
    }

    public static TransformCommand snapshotCoalescing(GameObject target) {
        Transform3D transform = transformOf(target);
        return new TransformCommand(target, transform.position(), transform.rotation(), transform.scale(), true);
    }

    @Override
    public void apply(CommandContext context) {
        Transform3D transform = transformOf(target);
        transform.setPosition(position.x, position.y, position.z);
        transform.setRotation(rotation);
        transform.setScale(scale.x, scale.y, scale.z);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        Transform3D transform = transformOf(target);
        return new TransformCommand(target, transform.position(), transform.rotation(), transform.scale(), coalescing);
    }

    @Override
    public String coalesceKey() {
        return coalescing ? "transform:" + System.identityHashCode(target) : null;
    }

    @Override
    public String label() {
        return "Transform " + target.name();
    }

    private static Transform3D transformOf(GameObject gameObject) {
        return gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new IllegalStateException(
                        "TransformCommand target has no Transform3D: " + gameObject.name()));
    }
}
