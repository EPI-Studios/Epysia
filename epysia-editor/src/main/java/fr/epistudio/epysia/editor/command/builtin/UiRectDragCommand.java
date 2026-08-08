package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.ui.UiElement;
import org.joml.Vector4f;

public final class UiRectDragCommand implements EditorCommand {

    private final UiElement element;
    private final Vector4f beforePosition;
    private final Vector4f beforeSize;
    private final Vector4f afterPosition;
    private final Vector4f afterSize;

    public UiRectDragCommand(UiElement element, Vector4f beforePosition, Vector4f beforeSize,
                             Vector4f afterPosition, Vector4f afterSize) {
        this.element = element;
        this.beforePosition = new Vector4f(beforePosition);
        this.beforeSize = new Vector4f(beforeSize);
        this.afterPosition = new Vector4f(afterPosition);
        this.afterSize = new Vector4f(afterSize);
    }

    @Override
    public void apply(CommandContext context) {
        element.position().set(afterPosition);
        element.size().set(afterSize);
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new UiRectDragCommand(element, afterPosition, afterSize, beforePosition, beforeSize);
    }

    @Override
    public String label() {
        return "Ui Rect";
    }
}
