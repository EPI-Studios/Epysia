package fr.epistudio.epysia.input;

public final class MutableInputState implements InputState {

    private final boolean[] keyStates = new boolean[KeyCode.values().length];
    private final boolean[] mouseButtonStates = new boolean[MouseButton.values().length];
    private float cursorX;
    private float cursorY;
    private float scrollDeltaY;
    private final boolean[] previousKeyStates = new boolean[KeyCode.values().length];
    private final boolean[] previousMouseButtonStates = new boolean[MouseButton.values().length];
    private float previousCursorX;
    private float previousCursorY;

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

    @Override
    public boolean wasKeyPressed(KeyCode key) {
        return keyStates[key.ordinal()] && !previousKeyStates[key.ordinal()];
    }

    @Override
    public boolean wasKeyReleased(KeyCode key) {
        return !keyStates[key.ordinal()] && previousKeyStates[key.ordinal()];
    }

    @Override
    public boolean wasMouseButtonPressed(MouseButton button) {
        return mouseButtonStates[button.ordinal()] && !previousMouseButtonStates[button.ordinal()];
    }

    @Override
    public boolean wasMouseButtonReleased(MouseButton button) {
        return !mouseButtonStates[button.ordinal()] && previousMouseButtonStates[button.ordinal()];
    }

    @Override
    public float mouseDeltaX() {
        return cursorX - previousCursorX;
    }

    @Override
    public float mouseDeltaY() {
        return cursorY - previousCursorY;
    }

    public void advanceFrame() {
        System.arraycopy(keyStates, 0, previousKeyStates, 0, keyStates.length);
        System.arraycopy(mouseButtonStates, 0, previousMouseButtonStates, 0, mouseButtonStates.length);
        previousCursorX = cursorX;
        previousCursorY = cursorY;
        scrollDeltaY = 0.0f;
    }
}
