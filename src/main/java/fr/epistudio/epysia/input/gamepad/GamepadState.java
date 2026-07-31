package fr.epistudio.epysia.input.gamepad;

import java.util.Arrays;

public final class GamepadState {

    private static final GamepadState DISCONNECTED = new GamepadState();

    private final boolean[] buttonStates = new boolean[GamepadButton.values().length];
    private final boolean[] pressedLatches = new boolean[GamepadButton.values().length];
    private final boolean[] releasedLatches = new boolean[GamepadButton.values().length];
    private final float[] axisValues = new float[GamepadAxis.values().length];
    private boolean connected;
    private String name = "";

    public static GamepadState disconnected() {
        return DISCONNECTED;
    }

    public boolean isConnected() {
        return connected;
    }

    public String name() {
        return name;
    }

    public boolean isButtonDown(GamepadButton button) {
        return buttonStates[button.ordinal()];
    }

    public boolean wasButtonPressed(GamepadButton button) {
        return pressedLatches[button.ordinal()];
    }

    public boolean wasButtonReleased(GamepadButton button) {
        return releasedLatches[button.ordinal()];
    }

    public float axis(GamepadAxis axis) {
        return axisValues[axis.ordinal()];
    }

    void setConnected(boolean value, String deviceName) {
        connected = value;
        name = deviceName;
        if (!value) {
            clear();
        }
    }

    void setButton(GamepadButton button, boolean pressed) {
        int index = button.ordinal();
        if (pressed && !buttonStates[index]) {
            pressedLatches[index] = true;
        }
        if (!pressed && buttonStates[index]) {
            releasedLatches[index] = true;
        }
        buttonStates[index] = pressed;
    }

    void setAxis(GamepadAxis axis, float value) {
        axisValues[axis.ordinal()] = value;
    }

    void advanceFrame() {
        Arrays.fill(pressedLatches, false);
        Arrays.fill(releasedLatches, false);
    }

    private void clear() {
        Arrays.fill(buttonStates, false);
        Arrays.fill(axisValues, 0.0f);
    }
}
