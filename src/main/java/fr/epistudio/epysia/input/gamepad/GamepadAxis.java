package fr.epistudio.epysia.input.gamepad;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;

public enum GamepadAxis {

    LEFT_X(GLFW_GAMEPAD_AXIS_LEFT_X),
    LEFT_Y(GLFW_GAMEPAD_AXIS_LEFT_Y),
    RIGHT_X(GLFW_GAMEPAD_AXIS_RIGHT_X),
    RIGHT_Y(GLFW_GAMEPAD_AXIS_RIGHT_Y),
    LEFT_TRIGGER(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER),
    RIGHT_TRIGGER(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);

    private final int glfwAxis;

    GamepadAxis(int glfwAxis) {
        this.glfwAxis = glfwAxis;
    }

    public int glfwAxis() {
        return glfwAxis;
    }

    public boolean isTrigger() {
        return this == LEFT_TRIGGER || this == RIGHT_TRIGGER;
    }
}
