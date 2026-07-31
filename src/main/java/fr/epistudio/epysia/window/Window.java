package fr.epistudio.epysia.window;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.render.backend.RenderSurface;
import org.lwjgl.glfw.GLFWCharCallback;
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
import static org.lwjgl.glfw.GLFW.GLFW_RAW_MOUSE_MOTION;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.glfwRawMouseMotionSupported;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
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

    private static final String NATIVE_WAYLAND_PROPERTY = "epysia.window.nativeWayland";
    private static final String WIDTH_PROPERTY = "epysia.window.width";
    private static final String HEIGHT_PROPERTY = "epysia.window.height";

    private final String title;
    private int width;
    private int height;
    private long handle;
    private int framebufferWidth;
    private int framebufferHeight;
    private boolean vsyncEnabled = Boolean.parseBoolean(System.getProperty("epysia.vsync", "true"));
    private boolean framebufferResized;
    private MutableInputState attachedInput;
    private GLFWCharCallback charCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWScrollCallback scrollCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowSizeCallback windowSizeCallback;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = Integer.getInteger(WIDTH_PROPERTY, width);
        this.height = Integer.getInteger(HEIGHT_PROPERTY, height);
    }

    public static boolean offscreenRequested() {
        return Boolean.parseBoolean(System.getProperty("epysia.offscreen", "false"))
                || "1".equals(System.getenv("EPYSIA_OFFSCREEN"));
    }

    public void open() {
        applyPlatformHint();
        if (!glfwInit()) {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            if (!glfwInit()) {
                throw new EpysiaException("GLFW failed to initialize.");
            }
        }
        applyWindowHints();
        WindowIcon.hintApplicationClass();
        handle = glfwCreateWindow(width, height, title, 0L, 0L);
        if (handle == 0L) {
            glfwTerminate();
            throw new EpysiaException("GLFW failed to create a window.");
        }
        WindowIcon.applyDefault(handle);
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(vsyncEnabled && !offscreenRequested() ? 1 : 0);
        if (!offscreenRequested()) {
            glfwShowWindow(handle);
        }
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
        attachedInput = input;
        keyCallback = GLFWKeyCallback.create((window, glfwKey, scancode, action, mods) ->
                KeyCode.fromGlfw(glfwKey).ifPresent(key -> forwardKey(input, key, action, mods)));
        mouseButtonCallback = GLFWMouseButtonCallback.create((window, glfwButton, action, mods) ->
                MouseButton.fromGlfw(glfwButton).ifPresent(button ->
                        input.onMouseButton(button, action == GLFW_PRESS, mods)));
        cursorPosCallback = GLFWCursorPosCallback.create((window, x, y) -> input.onCursorPosition((float) x, (float) y));
        scrollCallback = GLFWScrollCallback.create((window, dx, dy) -> input.onScroll((float) dy));
        charCallback = GLFWCharCallback.create((window, codePoint) -> input.onTextTyped(codePoint));
        glfwSetKeyCallback(handle, keyCallback);
        glfwSetMouseButtonCallback(handle, mouseButtonCallback);
        glfwSetCursorPosCallback(handle, cursorPosCallback);
        glfwSetScrollCallback(handle, scrollCallback);
        glfwSetCharCallback(handle, charCallback);
    }

    private static void forwardKey(MutableInputState input, KeyCode key, int action, int modifiers) {
        if (action == GLFW_REPEAT) {
            input.onKeyRepeat(key, modifiers);
            return;
        }
        input.onKey(key, action == GLFW_PRESS, modifiers);
    }

    private void applyPlatformHint() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("linux")) {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            return;
        }
        if (Boolean.getBoolean(NATIVE_WAYLAND_PROPERTY)) {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            return;
        }
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
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT,
                Boolean.getBoolean("epysia.gl.debug") ? GLFW_TRUE : GLFW_FALSE);
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
        if (handle == 0L) {
            return;
        }
        int glfwMode = switch (mode) {
            case NORMAL -> GLFW_CURSOR_NORMAL;
            case HIDDEN -> GLFW_CURSOR_HIDDEN;
            case DISABLED -> GLFW_CURSOR_DISABLED;
        };
        glfwSetInputMode(handle, GLFW_CURSOR, glfwMode);
        applyRawMouseMotion(mode == CursorMode.DISABLED);
        resetCursorBaseline();
    }

    private void applyRawMouseMotion(boolean wanted) {
        if (!glfwRawMouseMotionSupported()) {
            return;
        }
        glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, wanted ? GLFW_TRUE : GLFW_FALSE);
    }

    private void resetCursorBaseline() {
        if (attachedInput == null) {
            return;
        }
        attachedInput.discardCursorBaseline();
    }

    public boolean rawMouseMotionSupported() {
        return glfwRawMouseMotionSupported();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(handle, true);
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
        if (charCallback != null) {
            charCallback.free();
            charCallback = null;
        }
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
