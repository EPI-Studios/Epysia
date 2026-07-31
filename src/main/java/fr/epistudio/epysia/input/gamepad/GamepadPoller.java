package fr.epistudio.epysia.input.gamepad;

import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetGamepadName;
import static org.lwjgl.glfw.GLFW.glfwGetGamepadState;
import static org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad;
import static org.lwjgl.glfw.GLFW.glfwJoystickPresent;

public final class GamepadPoller {

    public static final int MAXIMUM_GAMEPADS = 4;

    private static final float DEFAULT_STICK_DEADZONE = 0.15f;
    private static final float DEFAULT_TRIGGER_DEADZONE = 0.06f;

    private final GamepadState[] gamepads = new GamepadState[MAXIMUM_GAMEPADS];
    private float stickDeadzone = DEFAULT_STICK_DEADZONE;
    private float triggerDeadzone = DEFAULT_TRIGGER_DEADZONE;

    public GamepadPoller() {
        for (int index = 0; index < gamepads.length; index++) {
            gamepads[index] = new GamepadState();
        }
    }

    public GamepadState gamepad(int index) {
        if (index < 0 || index >= gamepads.length) {
            return GamepadState.disconnected();
        }
        return gamepads[index];
    }

    public void setStickDeadzone(float value) {
        stickDeadzone = Math.clamp(value, 0.0f, 0.9f);
    }

    public void setTriggerDeadzone(float value) {
        triggerDeadzone = Math.clamp(value, 0.0f, 0.9f);
    }

    public void poll() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState raw = GLFWGamepadState.malloc(stack);
            for (int index = 0; index < gamepads.length; index++) {
                pollOne(index, raw);
            }
        }
    }

    private void pollOne(int index, GLFWGamepadState raw) {
        int joystick = GLFW_JOYSTICK_1 + index;
        GamepadState gamepad = gamepads[index];
        if (!glfwJoystickPresent(joystick) || !glfwJoystickIsGamepad(joystick)
                || !glfwGetGamepadState(joystick, raw)) {
            gamepad.setConnected(false, "");
            return;
        }
        String name = glfwGetGamepadName(joystick);
        gamepad.setConnected(true, name == null ? "" : name);
        readButtons(gamepad, raw);
        readSticks(gamepad, raw);
        readTriggers(gamepad, raw);
    }

    private static void readButtons(GamepadState gamepad, GLFWGamepadState raw) {
        for (GamepadButton button : GamepadButton.values()) {
            gamepad.setButton(button, raw.buttons(button.glfwButton()) == GLFW_PRESS);
        }
    }

    private void readSticks(GamepadState gamepad, GLFWGamepadState raw) {
        applyStick(gamepad, raw, GamepadAxis.LEFT_X, GamepadAxis.LEFT_Y);
        applyStick(gamepad, raw, GamepadAxis.RIGHT_X, GamepadAxis.RIGHT_Y);
    }

    private void applyStick(GamepadState gamepad, GLFWGamepadState raw,
                            GamepadAxis horizontal, GamepadAxis vertical) {
        float x = raw.axes(horizontal.glfwAxis());
        float y = raw.axes(vertical.glfwAxis());
        float magnitude = (float) Math.sqrt(x * x + y * y);
        if (magnitude <= stickDeadzone) {
            gamepad.setAxis(horizontal, 0.0f);
            gamepad.setAxis(vertical, 0.0f);
            return;
        }
        float rescaled = Math.min(1.0f, (magnitude - stickDeadzone) / (1.0f - stickDeadzone)) / magnitude;
        gamepad.setAxis(horizontal, x * rescaled);
        gamepad.setAxis(vertical, y * rescaled);
    }

    private void readTriggers(GamepadState gamepad, GLFWGamepadState raw) {
        applyTrigger(gamepad, raw, GamepadAxis.LEFT_TRIGGER);
        applyTrigger(gamepad, raw, GamepadAxis.RIGHT_TRIGGER);
    }

    private void applyTrigger(GamepadState gamepad, GLFWGamepadState raw, GamepadAxis trigger) {
        float pressed = (raw.axes(trigger.glfwAxis()) + 1.0f) * 0.5f;
        if (pressed <= triggerDeadzone) {
            gamepad.setAxis(trigger, 0.0f);
            return;
        }
        gamepad.setAxis(trigger, (pressed - triggerDeadzone) / (1.0f - triggerDeadzone));
    }

    public void advanceFrame() {
        for (GamepadState gamepad : gamepads) {
            gamepad.advanceFrame();
        }
    }
}
