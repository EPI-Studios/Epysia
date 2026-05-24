package fr.epistudio.epysia.window;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.render.backend.RenderSurface;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.GLFW_ANY_PLATFORM;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_X11;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwInitHint;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;

public final class Window implements RenderSurface {

    private final String title;
    private int width;
    private int height;
    private long handle;
    private int framebufferWidth;
    private int framebufferHeight;
    private boolean vsyncEnabled = false;
    private boolean framebufferResized;
    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWScrollCallback scrollCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowSizeCallback windowSizeCallback;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public void open() {
        forceX11Platform();
        if (!glfwInit()) {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            if (!glfwInit()) {
                throw new EpysiaException("GLFW failed to initialize.");
            }
        }
        applyWindowHints();
        handle = glfwCreateWindow(width, height, title, 0L, 0L);
        if (handle == 0L) {
            glfwTerminate();
            throw new EpysiaException("GLFW failed to create a window.");
        }
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(vsyncEnabled ? 1 : 0);
        glfwShowWindow(handle);
        refreshFramebufferSize();
        refreshWindowSize();
        installResizeCallbacks();
    }

    private void installResizeCallbacks() {
        framebufferSizeCallback = GLFWFramebufferSizeCallback.create((window, w, h) -> {
            framebufferWidth = w;
            framebufferHeight = h;
            framebufferResized = true;
        });
        windowSizeCallback = GLFWWindowSizeCallback.create((window, w, h) -> {
            width = w;
            height = h;
        });
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback);
        glfwSetWindowSizeCallback(handle, windowSizeCallback);
    }

    public boolean consumeFramebufferResized() {
        if (!framebufferResized) {
            return false;
        }
        framebufferResized = false;
        return true;
    }

    private void refreshWindowSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            glfwGetWindowSize(handle, widthBuffer, heightBuffer);
            width = widthBuffer.get(0);
            height = heightBuffer.get(0);
        }
    }

    public void attachInput(MutableInputState input) {
        keyCallback = GLFWKeyCallback.create((window, glfwKey, scancode, action, mods) -> {
            if (action == GLFW_REPEAT) {
                return;
            }
            KeyCode.fromGlfw(glfwKey).ifPresent(key -> input.onKey(key, action == GLFW_PRESS));
        });
        mouseButtonCallback = GLFWMouseButtonCallback.create((window, glfwButton, action, mods) ->
                MouseButton.fromGlfw(glfwButton).ifPresent(button -> input.onMouseButton(button, action == GLFW_PRESS)));
        cursorPosCallback = GLFWCursorPosCallback.create((window, x, y) -> input.onCursorPosition((float) x, (float) y));
        scrollCallback = GLFWScrollCallback.create((window, dx, dy) -> input.onScroll((float) dy));
        glfwSetKeyCallback(handle, keyCallback);
        glfwSetMouseButtonCallback(handle, mouseButtonCallback);
        glfwSetCursorPosCallback(handle, cursorPosCallback);
        glfwSetScrollCallback(handle, scrollCallback);
    }

    private void forceX11Platform() {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
    }

    private void applyWindowHints() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
    }

    private void refreshFramebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer.get(0);
            framebufferHeight = heightBuffer.get(0);
        }
    }

    public void setVsync(boolean enabled) {
        this.vsyncEnabled = enabled;
        if (handle != 0L) {
            glfwSwapInterval(enabled ? 1 : 0);
        }
    }

    public boolean vsyncEnabled() {
        return vsyncEnabled;
    }

    public void setCursorMode(CursorMode mode) {
        int glfwMode = switch (mode) {
            case NORMAL -> GLFW_CURSOR_NORMAL;
            case HIDDEN -> GLFW_CURSOR_HIDDEN;
            case DISABLED -> GLFW_CURSOR_DISABLED;
        };
        glfwSetInputMode(handle, GLFW_CURSOR, glfwMode);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public void close() {
        freeCallbacks();
        if (handle != 0L) {
            glfwDestroyWindow(handle);
            handle = 0L;
        }
        glfwTerminate();
    }

    private void freeCallbacks() {
        if (keyCallback != null) {
            keyCallback.free();
            keyCallback = null;
        }
        if (mouseButtonCallback != null) {
            mouseButtonCallback.free();
            mouseButtonCallback = null;
        }
        if (cursorPosCallback != null) {
            cursorPosCallback.free();
            cursorPosCallback = null;
        }
        if (scrollCallback != null) {
            scrollCallback.free();
            scrollCallback = null;
        }
        if (framebufferSizeCallback != null) {
            framebufferSizeCallback.free();
            framebufferSizeCallback = null;
        }
        if (windowSizeCallback != null) {
            windowSizeCallback.free();
            windowSizeCallback = null;
        }
    }

    public long handle() {
        return handle;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }
}
