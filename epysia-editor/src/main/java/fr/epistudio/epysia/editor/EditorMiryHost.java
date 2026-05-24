package fr.epistudio.epysia.editor;

import com.miry.core.Input;
import com.miry.platform.MiryHost;
import fr.epistudio.epysia.window.Window;
import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.glfwGetClipboardString;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwSetClipboardString;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;

public final class EditorMiryHost implements MiryHost {

    private final Window window;
    private final Vector2f scratchMousePosition = new Vector2f();

    public EditorMiryHost(Window window) {
        this.window = window;
    }

    @Override
    public int getWindowWidth() {
        return window.width();
    }

    @Override
    public int getWindowHeight() {
        return window.height();
    }

    @Override
    public int getFramebufferWidth() {
        return window.framebufferWidth();
    }

    @Override
    public int getFramebufferHeight() {
        return window.framebufferHeight();
    }

    @Override
    public float getScaleFactor() {
        if (window.width() <= 0) {
            return 1.0f;
        }
        return (float) window.framebufferWidth() / window.width();
    }

    @Override
    public double getTime() {
        return glfwGetTime();
    }

    @Override
    public boolean isKeyDown(int key) {
        return Input.isKeyDown(key);
    }

    @Override
    public boolean isMouseDown(int button) {
        return Input.isMouseButtonDown(button);
    }

    @Override
    public Vector2f getMousePos() {
        scratchMousePosition.set((float) Input.getMouseX(), (float) Input.getMouseY());
        return scratchMousePosition;
    }

    @Override
    public void setCursorLocked(boolean locked) {
        glfwSetInputMode(window.handle(), GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    @Override
    public String getClipboard() {
        String value = glfwGetClipboardString(window.handle());
        return value == null ? "" : value;
    }

    @Override
    public void setClipboard(String text) {
        glfwSetClipboardString(window.handle(), text == null ? "" : text);
    }

    @Override
    public long getNativeWindow() {
        return window.handle();
    }
}
