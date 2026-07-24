package fr.epistudio.epysia.input;

enum InactiveInputState implements InputState {
    INSTANCE;

    @Override
    public boolean isKeyDown(KeyCode key) { return false; }

    @Override
    public boolean isMouseButtonDown(MouseButton button) { return false; }

    @Override
    public float cursorX() { return 0.0f; }

    @Override
    public float cursorY() { return 0.0f; }

    @Override
    public float scrollDeltaY() { return 0.0f; }
}
