package fr.epistudio.epysia.editor.input;

public interface InputLayer {

    String name();

    int priority();

    default boolean enabled() {
        return true;
    }

    default boolean onMouse(MouseEvent event) {
        return false;
    }

    default boolean onKey(KeyEvent event) {
        return false;
    }
}
