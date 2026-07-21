package fr.epistudio.epysia.editor.shell;

import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glViewport;

public final class ImGuiShell {

    private static final String WINDOW_TITLE = "Epysia Editor";
    private static final int WINDOW_WIDTH = 1600;
    private static final int WINDOW_HEIGHT = 900;
    private static final int OPENGL_MAJOR = 4;
    private static final int OPENGL_MINOR = 3;
    private static final String GLSL_VERSION = "#version 430";
    private static final String FONT_RESOURCE = "/fonts/inter.ttf";
    private static final String MONOSPACE_FONT_RESOURCE = "/fonts/noto-sans-mono.ttf";
    private static final float CLEAR_RED = 0.117f;
    private static final float CLEAR_GREEN = 0.117f;
    private static final float CLEAR_BLUE = 0.117f;

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private long windowHandle;
    private long drawDataNanos;
    private long viewportsNanos;
    private long swapNanos;
    private ImFont monospaceFont;
    private Consumer<List<Path>> fileDropHandler = paths -> { };
    private boolean viewportsEnabled = true;
    private boolean vsyncEnabled = true;

    public boolean isVsyncEnabled() {
        return vsyncEnabled;
    }

    public void setVsyncEnabled(boolean enabled) {
        vsyncEnabled = enabled;
        GLFW.glfwSwapInterval(enabled ? 1 : 0);
    }

    public void setViewportsEnabled(boolean enabled) {
        this.viewportsEnabled = enabled;
    }

    public void setFileDropHandler(Consumer<List<Path>> handler) {
        this.fileDropHandler = handler;
    }

    public void clearFileDropHandler() {
        this.fileDropHandler = paths -> { };
    }

    public void initialize() {
        initializeGlfwWindow();
        initializeImGui();
    }

    private void initializeGlfwWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        applyPlatformHint();
        if (!GLFW.glfwInit()) {
            GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM);
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("GLFW initialization failed");
            }
        }
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, OPENGL_MAJOR);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, OPENGL_MINOR);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        windowHandle = GLFW.glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_TITLE, 0L, 0L);
        if (windowHandle == 0L) {
            throw new IllegalStateException("GLFW window creation failed");
        }
        GLFW.glfwMakeContextCurrent(windowHandle);
        GLFW.glfwSwapInterval(1);
        GL.createCapabilities();
        installDropCallback();
        GLFW.glfwShowWindow(windowHandle);
    }

    private void installDropCallback() {
        GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            List<Path> dropped = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                dropped.add(Path.of(GLFWDropCallback.getName(names, i)));
            }
            fileDropHandler.accept(dropped);
        });
    }

    private void applyPlatformHint() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("linux")) {
            GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM);
            return;
        }
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
    }

    private void initializeImGui() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        if (viewportsEnabled) {
            io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        }
        io.setIniFilename(null);
        ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setFontDataOwnedByAtlas(false);
        io.getFonts().addFontFromMemoryTTF(readFontBytes(FONT_RESOURCE), EditorStyle.FONT_PIXEL_HEIGHT, fontConfig);
        monospaceFont = io.getFonts().addFontFromMemoryTTF(readFontBytes(MONOSPACE_FONT_RESOURCE),
                EditorStyle.MONOSPACE_FONT_PIXEL_HEIGHT, fontConfig);
        io.getFonts().build();
        fontConfig.destroy();
        EditorStyle.setMonospaceFont(monospaceFont);
        EditorStyle.apply();
        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init(GLSL_VERSION);
    }

    public ImFont monospaceFont() {
        return monospaceFont;
    }

    private static byte[] readFontBytes(String fontResource) {
        try (InputStream stream = ImGuiShell.class.getResourceAsStream(fontResource)) {
            if (stream == null) {
                throw new UncheckedIOException(new IOException("Missing font resource " + fontResource));
            }
            return stream.readAllBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public long windowHandle() {
        return windowHandle;
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(windowHandle);
    }

    public void requestClose() {
        GLFW.glfwSetWindowShouldClose(windowHandle, true);
    }

    public void beginFrame() {
        GLFW.glfwPollEvents();
        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();
        ImGuizmo.beginFrame();
    }

    public void endFrame() {
        long renderStart = System.nanoTime();
        ImGui.render();
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, width, height);
        glViewport(0, 0, width[0], height[0]);
        glClearColor(CLEAR_RED, CLEAR_GREEN, CLEAR_BLUE, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        imGuiGl3.renderDrawData(ImGui.getDrawData());
        long drawDataEnd = System.nanoTime();
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            long contextBackup = GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            GLFW.glfwMakeContextCurrent(contextBackup);
        }
        long viewportsEnd = System.nanoTime();
        GLFW.glfwSwapBuffers(windowHandle);
        long swapEnd = System.nanoTime();
        drawDataNanos = drawDataEnd - renderStart;
        viewportsNanos = viewportsEnd - drawDataEnd;
        swapNanos = swapEnd - viewportsEnd;
    }

    public long drawDataNanos() {
        return drawDataNanos;
    }

    public long viewportsNanos() {
        return viewportsNanos;
    }

    public long swapNanos() {
        return swapNanos;
    }

    public int framebufferWidth() {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, width, height);
        return width[0];
    }

    public int framebufferHeight() {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, width, height);
        return height[0];
    }

    public void dispose() {
        imGuiGl3.shutdown();
        imGuiGlfw.shutdown();
        ImGui.destroyContext();
        GLFW.glfwDestroyWindow(windowHandle);
        GLFW.glfwTerminate();
    }
}
