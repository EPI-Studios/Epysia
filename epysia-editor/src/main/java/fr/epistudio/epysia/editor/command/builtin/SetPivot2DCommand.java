package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import org.joml.Vector2f;

public final class SetPivot2DCommand implements EditorCommand {

    private final Transform2D transform;
    private final Vector2f beforePivot;
    private final Vector2f beforePosition;
    private final Vector2f afterPivot;
    private final Vector2f afterPosition;

    public SetPivot2DCommand(Transform2D transform, Vector2f beforePivot, Vector2f beforePosition,
                             Vector2f afterPivot, Vector2f afterPosition) {
        this.transform = transform;
        this.beforePivot = new Vector2f(beforePivot);
        this.beforePosition = new Vector2f(beforePosition);
        this.afterPivot = new Vector2f(afterPivot);
        this.afterPosition = new Vector2f(afterPosition);
    }

    @Override
    public void apply(CommandContext context) {
        transform.setPivot(afterPivot.x, afterPivot.y);
        transform.setPosition(afterPosition.x, afterPosition.y);
        transform.markDirty();
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetPivot2DCommand(transform, afterPivot, afterPosition, beforePivot, beforePosition);
    }

    @Override
    public String label() {
        return "Pivot 2D";
    }
}
