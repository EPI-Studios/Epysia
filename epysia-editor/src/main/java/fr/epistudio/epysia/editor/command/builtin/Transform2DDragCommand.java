package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import org.joml.Vector2f;

public final class Transform2DDragCommand implements EditorCommand {

    private final Transform2D transform;
    private final Vector2f beforePosition;
    private final float beforeRotationRadians;
    private final Vector2f beforeScale;
    private final Vector2f afterPosition;
    private final float afterRotationRadians;
    private final Vector2f afterScale;

    public Transform2DDragCommand(Transform2D transform,
                                  Vector2f beforePosition, float beforeRotationRadians, Vector2f beforeScale,
                                  Vector2f afterPosition, float afterRotationRadians, Vector2f afterScale) {
        this.transform = transform;
        this.beforePosition = new Vector2f(beforePosition);
        this.beforeRotationRadians = beforeRotationRadians;
        this.beforeScale = new Vector2f(beforeScale);
        this.afterPosition = new Vector2f(afterPosition);
        this.afterRotationRadians = afterRotationRadians;
        this.afterScale = new Vector2f(afterScale);
    }

    @Override
    public void apply(CommandContext context) {
        transform.setPosition(afterPosition.x, afterPosition.y);
        transform.setRotationRadians(afterRotationRadians);
        transform.setScale(afterScale.x, afterScale.y);
        transform.markDirty();
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new Transform2DDragCommand(transform,
                afterPosition, afterRotationRadians, afterScale,
                beforePosition, beforeRotationRadians, beforeScale);
    }

    @Override
    public String label() {
        return "Transform 2D gizmo";
    }
}
