package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TransformDragCommand implements EditorCommand {

    private final Transform3D transform;
    private final Vector3f beforePosition;
    private final Quaternionf beforeRotation;
    private final Vector3f beforeScale;
    private final Vector3f afterPosition;
    private final Quaternionf afterRotation;
    private final Vector3f afterScale;

    public TransformDragCommand(Transform3D transform,
                                Vector3f beforePosition, Quaternionf beforeRotation, Vector3f beforeScale,
                                Vector3f afterPosition, Quaternionf afterRotation, Vector3f afterScale) {
        this.transform = transform;
        this.beforePosition = new Vector3f(beforePosition);
        this.beforeRotation = new Quaternionf(beforeRotation);
        this.beforeScale = new Vector3f(beforeScale);
        this.afterPosition = new Vector3f(afterPosition);
        this.afterRotation = new Quaternionf(afterRotation);
        this.afterScale = new Vector3f(afterScale);
    }

    @Override
    public void apply(CommandContext context) {
        transform.setPosition(afterPosition.x, afterPosition.y, afterPosition.z);
        transform.setRotation(afterRotation);
        transform.setScale(afterScale.x, afterScale.y, afterScale.z);
        transform.markDirty();
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new TransformDragCommand(transform,
                afterPosition, afterRotation, afterScale,
                beforePosition, beforeRotation, beforeScale);
    }

    @Override
    public String label() {
        return "Transform gizmo";
    }
}
