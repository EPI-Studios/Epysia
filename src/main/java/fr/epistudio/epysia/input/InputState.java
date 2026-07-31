package fr.epistudio.epysia.input;

import fr.epistudio.epysia.input.gamepad.GamepadState;

import java.util.List;

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
    default boolean wasKeyRepeated(KeyCode key) { return false; }
    default boolean wasMouseButtonPressed(MouseButton button) { return false; }
    default boolean wasMouseButtonReleased(MouseButton button) { return false; }
    default float mouseDeltaX() { return 0.0f; }
    default float mouseDeltaY() { return 0.0f; }
    default boolean isModifierDown(KeyModifier modifier) { return false; }
    default String typedText() { return ""; }
    default GamepadState gamepad(int index) { return GamepadState.disconnected(); }
    default List<InputEvent> recentEvents() { return List.of(); }
    default boolean consumeBufferedKeyPress(KeyCode key, float withinSeconds) { return false; }
    default boolean consumeBufferedMouseButtonPress(MouseButton button, float withinSeconds) { return false; }
}
