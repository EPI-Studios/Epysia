package fr.epistudio.epysia.input;

public interface InputState {

    boolean isKeyDown(KeyCode key);

    boolean isMouseButtonDown(MouseButton button);

    float cursorX();

    float cursorY();

    float scrollDeltaY();
}
