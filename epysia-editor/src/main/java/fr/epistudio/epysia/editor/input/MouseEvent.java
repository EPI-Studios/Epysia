package fr.epistudio.epysia.editor.input;

public record MouseEvent(
        MouseEventKind kind,
        int button,
        float x,
        float y,
        float deltaX,
        float deltaY,
        float scrollY,
        boolean shift,
        boolean ctrl,
        boolean alt
) {
}
