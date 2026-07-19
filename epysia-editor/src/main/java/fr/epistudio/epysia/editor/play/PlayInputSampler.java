package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.MutableInputState;
import org.lwjgl.glfw.GLFW;

public final class PlayInputSampler {

    private final MutableInputState inputState = new MutableInputState();

    public MutableInputState inputState() {
        return inputState;
    }

    public void sample(long windowHandle, boolean active, float cursorX, float cursorY, float scrollDelta) {
        if (!active) {
            releaseAll();
            return;
        }
        for (KeyCode key : KeyCode.values()) {
            inputState.onKey(key, GLFW.glfwGetKey(windowHandle, key.glfwCode()) == GLFW.GLFW_PRESS);
        }
        for (MouseButton button : MouseButton.values()) {
            inputState.onMouseButton(button, GLFW.glfwGetMouseButton(windowHandle, button.glfwCode()) == GLFW.GLFW_PRESS);
        }
        inputState.onCursorPosition(cursorX, cursorY);
        if (scrollDelta != 0.0f) {
            inputState.onScroll(scrollDelta);
        }
    }

    private void releaseAll() {
        for (KeyCode key : KeyCode.values()) {
            inputState.onKey(key, false);
        }
        for (MouseButton button : MouseButton.values()) {
            inputState.onMouseButton(button, false);
        }
    }
}
