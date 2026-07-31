package fr.epistudio.epysia.input;

public record InputEvent(Kind kind, int code, Edge edge, int modifiers, double timeSeconds) {

    public enum Kind {
        KEY,
        MOUSE_BUTTON,
        GAMEPAD_BUTTON
    }

    public enum Edge {
        PRESSED,
        RELEASED,
        REPEATED
    }

    public static InputEvent key(KeyCode key, Edge edge, int modifiers, double timeSeconds) {
        return new InputEvent(Kind.KEY, key.ordinal(), edge, modifiers, timeSeconds);
    }

    public static InputEvent mouseButton(MouseButton button, Edge edge, int modifiers, double timeSeconds) {
        return new InputEvent(Kind.MOUSE_BUTTON, button.ordinal(), edge, modifiers, timeSeconds);
    }

    public boolean matches(KeyCode key) {
        return kind == Kind.KEY && code == key.ordinal();
    }

    public boolean matches(MouseButton button) {
        return kind == Kind.MOUSE_BUTTON && code == button.ordinal();
    }
}
