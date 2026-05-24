package fr.epistudio.epysia.editor.input;

public record KeyEvent(
        KeyEventKind kind,
        int key,
        boolean shift,
        boolean ctrl,
        boolean alt
) {
}
