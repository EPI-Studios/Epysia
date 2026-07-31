package fr.epistudio.epysia.input;

import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;

public enum KeyModifier {

    SHIFT(GLFW_MOD_SHIFT),
    CONTROL(GLFW_MOD_CONTROL),
    ALT(GLFW_MOD_ALT),
    SUPER(GLFW_MOD_SUPER);

    private final int glfwBit;

    KeyModifier(int glfwBit) {
        this.glfwBit = glfwBit;
    }

    public boolean isSetIn(int glfwModifiers) {
        return (glfwModifiers & glfwBit) != 0;
    }

    public static Optional<KeyModifier> named(String name) {
        for (KeyModifier modifier : values()) {
            if (modifier.name().equalsIgnoreCase(name)) {
                return Optional.of(modifier);
            }
        }
        return Optional.empty();
    }
}
