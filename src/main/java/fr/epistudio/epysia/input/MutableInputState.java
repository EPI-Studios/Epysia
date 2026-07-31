package fr.epistudio.epysia.input;

import fr.epistudio.epysia.input.gamepad.GamepadPoller;
import fr.epistudio.epysia.input.gamepad.GamepadState;

import java.util.Arrays;
import java.util.List;

public final class MutableInputState implements InputState {

    private static final float NEVER_PRESSED_SECONDS = Float.MAX_VALUE;

    private final boolean[] keyStates = new boolean[KeyCode.values().length];
    private final boolean[] keyPressedLatches = new boolean[KeyCode.values().length];
    private final boolean[] keyReleasedLatches = new boolean[KeyCode.values().length];
    private final boolean[] keyRepeatLatches = new boolean[KeyCode.values().length];
    private final double[] keyPressTimes = new double[KeyCode.values().length];
    private final boolean[] mouseButtonStates = new boolean[MouseButton.values().length];
    private final boolean[] mouseButtonPressedLatches = new boolean[MouseButton.values().length];
    private final boolean[] mouseButtonReleasedLatches = new boolean[MouseButton.values().length];
    private final double[] mouseButtonPressTimes = new double[MouseButton.values().length];
    private final StringBuilder typedText = new StringBuilder();
    private final InputEventRing eventRing = new InputEventRing();
    private final GamepadPoller gamepadPoller = new GamepadPoller();

    private float cursorX;
    private float cursorY;
    private float motionX;
    private float motionY;
    private float lastCursorX;
    private float lastCursorY;
    private boolean cursorBaselineKnown;
    private float scrollDeltaY;
    private int modifiers;
    private double timeSeconds;

    public MutableInputState() {
        Arrays.fill(keyPressTimes, Double.NEGATIVE_INFINITY);
        Arrays.fill(mouseButtonPressTimes, Double.NEGATIVE_INFINITY);
    }

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

    @Override
    public boolean wasKeyPressed(KeyCode key) {
        return keyPressedLatches[key.ordinal()];
    }

    @Override
    public boolean wasKeyReleased(KeyCode key) {
        return keyReleasedLatches[key.ordinal()];
    }

    @Override
    public boolean wasKeyRepeated(KeyCode key) {
        return keyRepeatLatches[key.ordinal()];
    }

    @Override
    public boolean wasMouseButtonPressed(MouseButton button) {
        return mouseButtonPressedLatches[button.ordinal()];
    }

    @Override
    public boolean wasMouseButtonReleased(MouseButton button) {
        return mouseButtonReleasedLatches[button.ordinal()];
    }

    @Override
    public float mouseDeltaX() {
        return motionX;
    }

    @Override
    public float mouseDeltaY() {
        return motionY;
    }

    @Override
    public boolean isModifierDown(KeyModifier modifier) {
        return modifier.isSetIn(modifiers);
    }

    @Override
    public String typedText() {
        return typedText.toString();
    }

    @Override
    public GamepadState gamepad(int index) {
        return gamepadPoller.gamepad(index);
    }

    @Override
    public List<InputEvent> recentEvents() {
        return eventRing.recent();
    }

    @Override
    public boolean consumeBufferedKeyPress(KeyCode key, float withinSeconds) {
        int index = key.ordinal();
        if (secondsSince(keyPressTimes[index]) > withinSeconds) {
            return false;
        }
        keyPressTimes[index] = Double.NEGATIVE_INFINITY;
        return true;
    }

    @Override
    public boolean consumeBufferedMouseButtonPress(MouseButton button, float withinSeconds) {
        int index = button.ordinal();
        if (secondsSince(mouseButtonPressTimes[index]) > withinSeconds) {
            return false;
        }
        mouseButtonPressTimes[index] = Double.NEGATIVE_INFINITY;
        return true;
    }

    private float secondsSince(double pressTime) {
        if (pressTime == Double.NEGATIVE_INFINITY) {
            return NEVER_PRESSED_SECONDS;
        }
        return (float) (timeSeconds - pressTime);
    }

    public GamepadPoller gamepads() {
        return gamepadPoller;
    }

    public void setTimeSeconds(double seconds) {
        timeSeconds = seconds;
    }

    public void onKey(KeyCode key, boolean pressed) {
        onKey(key, pressed, modifiers);
    }

    public void onKey(KeyCode key, boolean pressed, int glfwModifiers) {
        modifiers = glfwModifiers;
        int index = key.ordinal();
        if (pressed == keyStates[index]) {
            return;
        }
        keyStates[index] = pressed;
        if (pressed) {
            keyPressedLatches[index] = true;
            keyPressTimes[index] = timeSeconds;
        } else {
            keyReleasedLatches[index] = true;
        }
        eventRing.record(InputEvent.key(key,
                pressed ? InputEvent.Edge.PRESSED : InputEvent.Edge.RELEASED, glfwModifiers, timeSeconds));
    }

    public void onKeyRepeat(KeyCode key, int glfwModifiers) {
        modifiers = glfwModifiers;
        keyRepeatLatches[key.ordinal()] = true;
        eventRing.record(InputEvent.key(key, InputEvent.Edge.REPEATED, glfwModifiers, timeSeconds));
    }

    public void onMouseButton(MouseButton button, boolean pressed) {
        onMouseButton(button, pressed, modifiers);
    }

    public void onMouseButton(MouseButton button, boolean pressed, int glfwModifiers) {
        modifiers = glfwModifiers;
        int index = button.ordinal();
        if (pressed == mouseButtonStates[index]) {
            return;
        }
        mouseButtonStates[index] = pressed;
        if (pressed) {
            mouseButtonPressedLatches[index] = true;
            mouseButtonPressTimes[index] = timeSeconds;
        } else {
            mouseButtonReleasedLatches[index] = true;
        }
        eventRing.record(InputEvent.mouseButton(button,
                pressed ? InputEvent.Edge.PRESSED : InputEvent.Edge.RELEASED, glfwModifiers, timeSeconds));
    }

    public void onCursorPosition(float x, float y) {
        if (cursorBaselineKnown) {
            motionX += x - lastCursorX;
            motionY += y - lastCursorY;
        }
        lastCursorX = x;
        lastCursorY = y;
        cursorBaselineKnown = true;
        cursorX = x;
        cursorY = y;
    }

    public void onCursorWarp(float x, float y) {
        lastCursorX = x;
        lastCursorY = y;
        cursorBaselineKnown = true;
        cursorX = x;
        cursorY = y;
    }

    public void discardCursorBaseline() {
        cursorBaselineKnown = false;
    }

    public void onScroll(float deltaY) {
        scrollDeltaY += deltaY;
    }

    public void onTextTyped(int codePoint) {
        typedText.appendCodePoint(codePoint);
    }

    public void pollGamepads() {
        gamepadPoller.poll();
    }

    public void advanceFrame() {
        Arrays.fill(keyPressedLatches, false);
        Arrays.fill(keyReleasedLatches, false);
        Arrays.fill(keyRepeatLatches, false);
        Arrays.fill(mouseButtonPressedLatches, false);
        Arrays.fill(mouseButtonReleasedLatches, false);
        typedText.setLength(0);
        gamepadPoller.advanceFrame();
        motionX = 0.0f;
        motionY = 0.0f;
        scrollDeltaY = 0.0f;
    }
}
