package fr.epistudio.epysia.input;

public interface InputState {

    static InputState inactive() {
        return InactiveInputState.INSTANCE;
    }

    boolean isKeyDown(KeyCode key);

    boolean isMouseButtonDown(MouseButton button);

    float cursorX();

    float cursorY();

    float scrollDeltaY();

    default boolean wasKeyPressed(KeyCode key) { return false; }
    default boolean wasKeyReleased(KeyCode key) { return false; }
    default boolean wasMouseButtonPressed(MouseButton button) { return false; }
    default boolean wasMouseButtonReleased(MouseButton button) { return false; }
    default float mouseDeltaX() { return 0.0f; }
    default float mouseDeltaY() { return 0.0f; }
}
