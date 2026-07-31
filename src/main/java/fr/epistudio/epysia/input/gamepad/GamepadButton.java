package fr.epistudio.epysia.input.gamepad;

import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_GUIDE;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_START;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;

public enum GamepadButton {

    SOUTH(GLFW_GAMEPAD_BUTTON_A),
    EAST(GLFW_GAMEPAD_BUTTON_B),
    WEST(GLFW_GAMEPAD_BUTTON_X),
    NORTH(GLFW_GAMEPAD_BUTTON_Y),
    LEFT_BUMPER(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER),
    RIGHT_BUMPER(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER),
    BACK(GLFW_GAMEPAD_BUTTON_BACK),
    START(GLFW_GAMEPAD_BUTTON_START),
    GUIDE(GLFW_GAMEPAD_BUTTON_GUIDE),
    LEFT_THUMB(GLFW_GAMEPAD_BUTTON_LEFT_THUMB),
    RIGHT_THUMB(GLFW_GAMEPAD_BUTTON_RIGHT_THUMB),
    DPAD_UP(GLFW_GAMEPAD_BUTTON_DPAD_UP),
    DPAD_RIGHT(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT),
    DPAD_DOWN(GLFW_GAMEPAD_BUTTON_DPAD_DOWN),
    DPAD_LEFT(GLFW_GAMEPAD_BUTTON_DPAD_LEFT);

    private final int glfwButton;

    GamepadButton(int glfwButton) {
        this.glfwButton = glfwButton;
    }

    public int glfwButton() {
        return glfwButton;
    }

    public static Optional<GamepadButton> named(String name) {
        for (GamepadButton button : values()) {
            if (button.name().equalsIgnoreCase(name)) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }
}
