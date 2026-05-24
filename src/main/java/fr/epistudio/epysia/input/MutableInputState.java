package fr.epistudio.epysia.input;

public final class MutableInputState implements InputState {

    private final boolean[] keyStates = new boolean[KeyCode.values().length];
    private final boolean[] mouseButtonStates = new boolean[MouseButton.values().length];
    private float cursorX;
    private float cursorY;
    private float scrollDeltaY;

    @Override
    public boolean isKeyDown(KeyCode key) {
        return keyStates[key.ordinal()];
    }

    @Override
    public boolean isMouseButtonDown(MouseButton button) {
        return mouseButtonStates[button.ordinal()];
    }

    @Override
    public float cursorX() {
        return cursorX;
    }

    @Override
    public float cursorY() {
        return cursorY;
    }

    @Override
    public float scrollDeltaY() {
        return scrollDeltaY;
    }

    public void onKey(KeyCode key, boolean pressed) {
        keyStates[key.ordinal()] = pressed;
    }

    public void onMouseButton(MouseButton button, boolean pressed) {
        mouseButtonStates[button.ordinal()] = pressed;
    }

    public void onCursorPosition(float x, float y) {
        cursorX = x;
        cursorY = y;
    }

    public void onScroll(float deltaY) {
        scrollDeltaY += deltaY;
    }

    public void consumeFrameDeltas() {
        scrollDeltaY = 0.0f;
    }
}
