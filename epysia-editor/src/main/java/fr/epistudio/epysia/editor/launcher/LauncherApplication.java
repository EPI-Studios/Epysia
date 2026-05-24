package fr.epistudio.epysia.editor.launcher;

import com.miry.core.Application;
import com.miry.core.Input;
import com.miry.core.Window;
import com.miry.graphics.Framebuffer;
import com.miry.platform.InputConstants;
import com.miry.platform.MiryContext;
import com.miry.platform.MiryHost;
import com.miry.platform.glfw.GlfwFMiryHost;
import com.miry.ui.font.FontAtlas;
import com.miry.ui.font.FontData;
import com.miry.ui.font.TextRenderer;
import com.miry.ui.layout.LeafNode;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.editor.project.ProjectStore;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glViewport;

public final class LauncherApplication extends Application {

    private static final int BATCH_CAPACITY = 4_000;
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 640;
    private static final String WINDOW_TITLE = "Epysia Project Launcher";

    private LauncherPanel launcherPanel;
    private Window window;
    private MiryHost host;
    private Project chosenProject;

    public LauncherApplication() {
        super(BATCH_CAPACITY);
    }

    public Optional<Project> runUntilChosen() {
        bootstrapWindow();
        init();
        double previousTime = GLFW.glfwGetTime();
        while (!window.shouldClose()) {
            window.pollEvents();
            double currentTime = GLFW.glfwGetTime();
            float deltaTime = (float) Math.min(currentTime - previousTime, 0.1);
            previousTime = currentTime;
            update(deltaTime, host);
            render(host);
            window.swapBuffers();
        }
        shutdown();
        window.close();
        return Optional.ofNullable(chosenProject);
    }

    private void bootstrapWindow() {
        window = new Window(WINDOW_TITLE, WINDOW_WIDTH, WINDOW_HEIGHT);
        Input.init(window.getNativeWindow());
        host = new GlfwFMiryHost(window);
        MiryContext.setHost(host);
    }

    @Override
    protected void onInit() {
        installInterFont();
        EditorStyle.apply(theme);
        ProjectStore projectStore = new ProjectStore();
        launcherPanel = new LauncherPanel(projectStore, this::handleProjectChosen, this::handleError);
        LeafNode root = new LeafNode(launcherPanel);
        root.setBackgroundArgb(EditorStyle.COLOR_WINDOW_BG);
        root.setHeaderHeight(0);
        root.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        dockSpace.setRoot(root);
    }

    private void installInterFont() {
        ByteBuffer fontData = FontData.loadFromResource("/fonts/inter.ttf");
        float framebufferScale = Math.max(0.1f, MiryContext.host().getFramebufferScale());
        fontAtlas = new FontAtlas(fontData, 18.0f, 2048, framebufferScale, FontAtlas.Mode.COVERAGE);
        textRenderer = new TextRenderer(fontAtlas);
        batch.setTextRenderer(textRenderer);
    }

    @Override
    protected void onUpdate(float deltaTimeSeconds, MiryHost host) {
        synchronizeInput(host);
        ui.beginFrame(input, deltaTimeSeconds);
        uiContext.update(deltaTimeSeconds);
        toasts.update(deltaTimeSeconds);
        dockSpace.resize(host.getWindowWidth(), host.getWindowHeight());
        dockSpace.update(input);
        processEvents(false);
    }

    private void synchronizeInput(MiryHost host) {
        float cursorX = host.getMousePos().x;
        float cursorY = host.getMousePos().y;
        float scrollY = (float) Input.consumeScrollY();
        boolean leftDown = host.isMouseDown(InputConstants.MOUSE_BUTTON_LEFT);
        boolean leftPressed = leftDown && !prevLeft;
        boolean leftReleased = !leftDown && prevLeft;
        prevLeft = leftDown;
        input.setMousePos(cursorX, cursorY)
                .setMouseButtons(leftDown, leftPressed, leftReleased)
                .setModifiers(
                        host.isKeyDown(InputConstants.KEY_LEFT_CONTROL) || host.isKeyDown(InputConstants.KEY_RIGHT_CONTROL),
                        host.isKeyDown(InputConstants.KEY_LEFT_SHIFT) || host.isKeyDown(InputConstants.KEY_RIGHT_SHIFT),
                        host.isKeyDown(InputConstants.KEY_LEFT_ALT) || host.isKeyDown(InputConstants.KEY_RIGHT_ALT),
                        host.isKeyDown(InputConstants.KEY_LEFT_SUPER) || host.isKeyDown(InputConstants.KEY_RIGHT_SUPER))
                .setScrollY(scrollY);
    }

    @Override
    protected void onRender(MiryHost host) {
        int windowWidth = host.getWindowWidth();
        int windowHeight = host.getWindowHeight();
        int framebufferWidth = host.getFramebufferWidth();
        int framebufferHeight = host.getFramebufferHeight();
        uiFramebuffer.ensureSize(framebufferWidth, framebufferHeight);
        try (Framebuffer.Binding ignored = uiFramebuffer.bindScoped()) {
            Vector4f background = ui.theme().windowBg.toVector4f();
            glClearColor(background.x, background.y, background.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            batch.begin(windowWidth, windowHeight, host.getFramebufferScale());
            dockSpace.render(batch);
            uiContext.overlay().render(batch);
            toasts.render(batch, ui.theme(), windowWidth, windowHeight);
            batch.end();
        }
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        batch.begin(windowWidth, windowHeight, host.getFramebufferScale());
        batch.drawTexturedRect(uiFramebuffer.colorTexture(),
                0, 0, windowWidth, windowHeight, 0.0f, 1.0f, 1.0f, 0.0f, 0xFFFFFFFF);
        batch.end();
    }

    private void handleProjectChosen(Project project) {
        chosenProject = project;
        window.requestClose();
    }

    private void handleError(String message) {
        toasts.show(message, 3.5f);
    }
}
