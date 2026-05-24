package fr.epistudio.epysia.input;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum MouseButton {
    LEFT(GLFW.GLFW_MOUSE_BUTTON_LEFT),
    RIGHT(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
    MIDDLE(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

    private static final Map<Integer, MouseButton> BY_GLFW = buildLookup();

    private final int glfwCode;

    MouseButton(int glfwCode) {
        this.glfwCode = glfwCode;
    }

    public int glfwCode() {
        return glfwCode;
    }

    public static Optional<MouseButton> fromGlfw(int glfwCode) {
        return Optional.ofNullable(BY_GLFW.get(glfwCode));
    }

    private static Map<Integer, MouseButton> buildLookup() {
        Map<Integer, MouseButton> map = new HashMap<>();
        for (MouseButton button : values()) {
            map.put(button.glfwCode, button);
        }
        return Map.copyOf(map);
    }
}
