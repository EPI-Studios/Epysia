package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.scene.ColliderFit;
import fr.epistudio.epysia.physics.components.Collider;

public final class FitColliderCommand implements EditorCommand {

    private final Collider collider;
    private final ColliderFit.Shape before;
    private final ColliderFit.Shape after;

    public FitColliderCommand(Collider collider, ColliderFit.Shape before, ColliderFit.Shape after) {
        this.collider = collider;
        this.before = before;
        this.after = after;
    }

    @Override
    public void apply(CommandContext context) {
        ColliderFit.restore(collider, after);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new FitColliderCommand(collider, after, before);
    }

    @Override
    public String label() {
        return "Fit collider to mesh";
    }
}
