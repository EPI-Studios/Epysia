package fr.epistudio.epysia.editor;

import com.miry.core.Application;
import com.miry.core.Input;
import fr.epistudio.epysia.window.Window;
import com.miry.platform.InputConstants;
import com.miry.platform.MiryContext;
import com.miry.platform.MiryHost;
import com.miry.ui.font.FontAtlas;
import com.miry.ui.font.FontData;
import com.miry.ui.font.TextRenderer;
import com.miry.ui.layout.LeafNode;
import com.miry.ui.layout.SplitNode;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.logging.FanOutLogger;
import fr.epistudio.epysia.editor.panels.ConsolePanel;
import fr.epistudio.epysia.editor.panels.FileSystemPanel;
import fr.epistudio.epysia.editor.panels.InspectorPanel;
import fr.epistudio.epysia.editor.panels.SceneTreePanel;
import fr.epistudio.epysia.editor.panels.StatusBarPanel;
import fr.epistudio.epysia.editor.panels.TopBarPanel;
import fr.epistudio.epysia.editor.panels.ViewportPanel;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.logging.ConsoleLogger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

public final class EditorApplication extends Application {

    private static final int BATCH_CAPACITY = 30_000;
    private static final float TREE_PANEL_WIDTH_RATIO = 0.20f;
    private static final float INSPECTOR_PANEL_WIDTH_RATIO = 0.74f;
    private static final int INITIAL_VIEWPORT_WIDTH = 1280;
    private static final int INITIAL_VIEWPORT_HEIGHT = 720;
    private static final float CAMERA_LOOK_SENSITIVITY = 0.0035f;
    private static final float CAMERA_MOVE_SPEED = 6.0f;
    private static final float CAMERA_BOOST_MULTIPLIER = 3.0f;
    private static final float MAX_PITCH_RADIANS = (float) Math.toRadians(89.0);

    private SceneTreePanel sceneTreePanel;
    private ViewportPanel viewportPanel;
    private InspectorPanel inspectorPanel;
    private TopBarPanel topBarPanel;
    private StatusBarPanel statusBarPanel;
    private ConsolePanel consolePanel;
    private FileSystemPanel fileSystemPanel;
    private LeafNode leftLeaf;
    private SplitNode rootSplit;
    private SplitNode middleHorizontalSplit;
    private SplitNode topToMiddleSplit;
    private SplitNode middleWithConsoleSplit;
    private boolean consoleVisible = true;
    private static final float CONSOLE_SPLIT_RATIO = 0.75f;
    private final EditorSceneHost sceneHost = new EditorSceneHost();
    private final fr.epistudio.epysia.window.Window epysiaWindow;
    private final Project project;
    private EditorMiryInputState playInputState;
    private EditorWorld editorWorld;
    private GameWindow gameWindow;
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchMovement = new Vector3f();
    private float cameraYawRadians = (float) Math.toRadians(35.0);
    private float cameraPitchRadians = (float) Math.toRadians(-25.0);
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    private boolean cameraLookActive;
    private boolean cameraOrbitActive;
    private float lastOrbitMouseX = Float.NaN;
    private float lastOrbitMouseY = Float.NaN;
    private boolean previousFrameKeyFDown;
    private final Vector3f scratchEulerAngles = new Vector3f();
    private final Vector3f scratchPivot = new Vector3f();
    private final Vector3f scratchOrbitForward = new Vector3f();
    private static final float CAMERA_FRAME_DISTANCE = 3.5f;
    private static final float CAMERA_SCROLL_ZOOM_STEP = 0.5f;

    public EditorApplication(fr.epistudio.epysia.window.Window epysiaWindow, Project project) {
        super(BATCH_CAPACITY);
        this.epysiaWindow = epysiaWindow;
        this.project = project;
    }

    public void runLoop(Window window) {
        init();
        double previousTime = org.lwjgl.glfw.GLFW.glfwGetTime();
        while (!window.shouldClose()) {
            window.pollEvents();
            double currentTime = org.lwjgl.glfw.GLFW.glfwGetTime();
            float deltaTimeSeconds = (float) Math.min(currentTime - previousTime, 0.1);
            previousTime = currentTime;
            onUpdate(deltaTimeSeconds, com.miry.platform.MiryContext.host());
            onRender(com.miry.platform.MiryContext.host());
            window.swapBuffers();
        }
        onShutdown();
    }

    @Override
    protected void onInit() {
        installInterFont();
        EditorStyle.apply(theme);
        consolePanel = new ConsolePanel();
        sceneHost.setLogger(new FanOutLogger(java.util.List.of(new ConsoleLogger(), consolePanel)));
        sceneHost.initialize(INITIAL_VIEWPORT_WIDTH, INITIAL_VIEWPORT_HEIGHT);
        populateComponentRegistryFromScan();
        EditorContextImpl editorContext = new EditorContextImpl(sceneHost.primitiveRegistry());
        fr.epistudio.epysia.SystemRegistryImpl systemRegistry = new fr.epistudio.epysia.SystemRegistryImpl();
        loadAndApplyEngineModules(systemRegistry, editorContext);
        EditorEngineServices engineServices = new EditorEngineServices(
                epysiaWindow,
                sceneHost.backend(),
                null,
                sceneHost.scene(),
                systemRegistry);
        editorWorld = new EditorWorld(sceneHost.scene(), systemRegistry.systems(), engineServices,
                sceneHost.editorCameraObject());
        fr.epistudio.epysia.editor.play.SubprocessPlayController playController =
                new fr.epistudio.epysia.editor.play.SubprocessPlayController(sceneHost, project, consolePanel);
        editorWorld.setPlayController(playController);
        syncCameraStateFromTransform();
        playInputState = new EditorMiryInputState(MiryContext.host());
        sceneTreePanel = new SceneTreePanel(editorWorld, sceneHost);
        viewportPanel = new ViewportPanel(sceneHost, editorWorld);
        inspectorPanel = new InspectorPanel(editorWorld, sceneHost);
        topBarPanel = new TopBarPanel(editorWorld, sceneHost, project, toasts,
                epysiaWindow::requestClose,
                this::toggleConsoleVisible,
                this::toggleFileSystemTab);
        statusBarPanel = new StatusBarPanel(editorWorld, sceneHost);
        fileSystemPanel = new FileSystemPanel(project, toasts);
        dockSpace.setRoot(buildDockLayout());
    }

    private void installInterFont() {
        ByteBuffer fontData = FontData.loadFromResource("/fonts/inter.ttf");
        float framebufferScale = Math.max(0.1f, MiryContext.host().getFramebufferScale());
        int atlasSize = 2048;
        fontAtlas = new FontAtlas(fontData, 18.0f, atlasSize, framebufferScale, FontAtlas.Mode.COVERAGE);
        textRenderer = new TextRenderer(fontAtlas);
        batch.setTextRenderer(textRenderer);
    }

    private void populateComponentRegistryFromScan() {
        java.util.List<fr.epistudio.epysia.reflection.DiscoveredComponent> discovered =
                fr.epistudio.epysia.reflection.ComponentScanner.scan();
        sceneHost.components().populateFromScan(discovered);
    }

    private void loadAndApplyEngineModules(fr.epistudio.epysia.SystemRegistryImpl systemRegistry,
                                           EditorContextImpl editorContext) {
        java.util.List<fr.epistudio.epysia.EngineModule> modules = new java.util.ArrayList<>();
        for (fr.epistudio.epysia.EngineModule module
                : java.util.ServiceLoader.load(fr.epistudio.epysia.EngineModule.class)) {
            modules.add(module);
        }
        modules.sort(java.util.Comparator.comparingInt(fr.epistudio.epysia.EngineModule::order));
        for (fr.epistudio.epysia.EngineModule module : modules) {
            module.registerSystems(systemRegistry);
            module.registerEditorExtensions(editorContext);
        }
    }

    private SplitNode buildDockLayout() {
        leftLeaf = leafFor(sceneTreePanel);
        leftLeaf.addTab(fileSystemPanel);
        LeafNode viewportLeaf = leafFor(viewportPanel);
        LeafNode inspectorLeaf = leafFor(inspectorPanel);
        LeafNode topBarLeaf = chromelessLeafFor(topBarPanel);
        LeafNode statusBarLeaf = chromelessLeafFor(statusBarPanel);
        LeafNode consoleLeaf = chromelessLeafFor(consolePanel);
        middleHorizontalSplit = new SplitNode(viewportLeaf, inspectorLeaf, false, INSPECTOR_PANEL_WIDTH_RATIO);
        SplitNode middleRow = new SplitNode(leftLeaf, middleHorizontalSplit, false, TREE_PANEL_WIDTH_RATIO);
        middleWithConsoleSplit = new SplitNode(middleRow, consoleLeaf, true, CONSOLE_SPLIT_RATIO);
        topToMiddleSplit = new SplitNode(topBarLeaf, middleWithConsoleSplit, true, 0.05f);
        rootSplit = new SplitNode(topToMiddleSplit, statusBarLeaf, true, 0.95f);
        return rootSplit;
    }

    private void toggleConsoleVisible() {
        consoleVisible = !consoleVisible;
        middleWithConsoleSplit.splitRatio = consoleVisible ? CONSOLE_SPLIT_RATIO : 1.0f;
    }

    private void toggleFileSystemTab() {
        if (leftLeaf == null) {
            return;
        }
        int target = leftLeaf.activeTabIndex() == 0 ? 1 : 0;
        leftLeaf.setActiveTabIndex(target);
    }

    private LeafNode leafFor(com.miry.ui.panels.Panel panel) {
        LeafNode leaf = new LeafNode(panel);
        leaf.setBackgroundArgb(EditorStyle.LEAF_BACKGROUND);
        leaf.setHeaderHeight(EditorStyle.LEAF_HEADER_HEIGHT);
        leaf.setHeaderArgb(EditorStyle.LEAF_HEADER_BG);
        leaf.setHeaderAccentArgb(EditorStyle.LEAF_HEADER_ACCENT);
        leaf.setHeaderButtons(LeafNode.HeaderButtons.CLOSE_ONLY);
        leaf.setHeaderButtonsOnlyOnHover(true);
        return leaf;
    }

    private LeafNode chromelessLeafFor(com.miry.ui.panels.Panel panel) {
        LeafNode leaf = new LeafNode(panel);
        leaf.setBackgroundArgb(EditorStyle.COLOR_WINDOW_BG);
        leaf.setHeaderHeight(0);
        leaf.setHeaderButtons(LeafNode.HeaderButtons.NONE);
        return leaf;
    }

    @Override
    protected void onUpdate(float deltaTimeSeconds, MiryHost host) {
        synchronizeInput(host);
        handleHistoryShortcuts(host);
        handleFrameSelectedKey(host);
        applyScrollZoom();
        viewportPanel.tickKeyboard(host);
        editorWorld.advancePlayClock(deltaTimeSeconds);
        editorWorld.playRuntime().tick(playInputState, deltaTimeSeconds);
        synchronizeGameWindowWithPlayState();
        statusBarPanel.tickFps(deltaTimeSeconds);
        updateEditorCamera(host, deltaTimeSeconds);
        ui.beginFrame(input, deltaTimeSeconds);
        uiContext.update(deltaTimeSeconds);
        toasts.update(deltaTimeSeconds);
        int windowHeight = Math.max(1, host.getWindowHeight());
        topToMiddleSplit.splitRatio = clamp01((float) EditorStyle.TOPBAR_HEIGHT / windowHeight);
        rootSplit.splitRatio = clamp01(1.0f - (float) EditorStyle.STATUSBAR_HEIGHT / windowHeight);
        dockSpace.resize(host.getWindowWidth(), windowHeight);
        dockSpace.update(input);
        processEvents(false);
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    private boolean playToastShown;

    private void synchronizeGameWindowWithPlayState() {
        boolean playing = editorWorld.isPlaying();
        if (playing && !playToastShown) {
            toasts.show("▶ Playing in subprocess", 2.5f);
            playToastShown = true;
        } else if (!playing && playToastShown) {
            toasts.show("■ Stopped", 1.5f);
            playToastShown = false;
        }
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

    private void updateEditorCamera(MiryHost host, float deltaTimeSeconds) {
        boolean altDown = host.isKeyDown(InputConstants.KEY_LEFT_ALT) || host.isKeyDown(InputConstants.KEY_RIGHT_ALT);
        boolean leftDown = host.isMouseDown(InputConstants.MOUSE_BUTTON_LEFT);
        boolean rightDown = host.isMouseDown(InputConstants.MOUSE_BUTTON_RIGHT);
        if (altDown && leftDown) {
            updateOrbit(host);
            return;
        }
        cameraOrbitActive = false;
        if (!rightDown) {
            cameraLookActive = false;
            return;
        }
        updateFlyLook(host);
        applyCameraOrientation();
        applyKeyboardMovement(host, deltaTimeSeconds);
    }

    private void updateFlyLook(MiryHost host) {
        float cursorX = host.getMousePos().x;
        float cursorY = host.getMousePos().y;
        if (!cameraLookActive) {
            cameraLookActive = true;
            lastMouseX = cursorX;
            lastMouseY = cursorY;
        }
        cameraYawRadians -= (cursorX - lastMouseX) * CAMERA_LOOK_SENSITIVITY;
        cameraPitchRadians = clamp(cameraPitchRadians - (cursorY - lastMouseY) * CAMERA_LOOK_SENSITIVITY,
                -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        lastMouseX = cursorX;
        lastMouseY = cursorY;
    }

    private void updateOrbit(MiryHost host) {
        float cursorX = host.getMousePos().x;
        float cursorY = host.getMousePos().y;
        if (!cameraOrbitActive) {
            cameraOrbitActive = true;
            lastOrbitMouseX = cursorX;
            lastOrbitMouseY = cursorY;
        }
        cameraYawRadians -= (cursorX - lastOrbitMouseX) * CAMERA_LOOK_SENSITIVITY;
        cameraPitchRadians = clamp(cameraPitchRadians - (cursorY - lastOrbitMouseY) * CAMERA_LOOK_SENSITIVITY,
                -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        lastOrbitMouseX = cursorX;
        lastOrbitMouseY = cursorY;
        applyOrbitPlacement();
        applyCameraOrientation();
    }

    private void applyOrbitPlacement() {
        Transform3D transform = sceneHost.cameraTransform();
        resolveOrbitPivot(scratchPivot);
        float distance = transform.position().distance(scratchPivot);
        if (distance < 0.001f) {
            distance = CAMERA_FRAME_DISTANCE;
        }
        scratchRotation.identity().rotateY(cameraYawRadians).rotateX(cameraPitchRadians)
                .transform(0.0f, 0.0f, -1.0f, scratchOrbitForward);
        transform.setPosition(
                scratchPivot.x - scratchOrbitForward.x * distance,
                scratchPivot.y - scratchOrbitForward.y * distance,
                scratchPivot.z - scratchOrbitForward.z * distance);
    }

    private void resolveOrbitPivot(Vector3f target) {
        target.set(0.0f);
        editorWorld.selected().ifPresent(selected ->
                selected.getComponent(Transform3D.class).ifPresent(transform -> target.set(transform.position())));
    }

    private void syncCameraStateFromTransform() {
        Quaternionf rotation = sceneHost.cameraTransform().rotation();
        rotation.getEulerAnglesYXZ(scratchEulerAngles);
        cameraYawRadians = scratchEulerAngles.y;
        cameraPitchRadians = scratchEulerAngles.x;
    }

    private boolean undoKeyHeld;
    private boolean redoKeyHeld;

    private void handleHistoryShortcuts(MiryHost host) {
        boolean ctrl = host.isKeyDown(InputConstants.KEY_LEFT_CONTROL) || host.isKeyDown(InputConstants.KEY_RIGHT_CONTROL);
        boolean shift = host.isKeyDown(InputConstants.KEY_LEFT_SHIFT) || host.isKeyDown(InputConstants.KEY_RIGHT_SHIFT);
        boolean zDown = host.isKeyDown(InputConstants.KEY_Z);
        boolean yDown = host.isKeyDown(InputConstants.KEY_Y);
        boolean undoRequested = ctrl && zDown && !shift;
        boolean redoRequested = ctrl && (yDown || (zDown && shift));
        if (undoRequested && !undoKeyHeld) {
            if (editorWorld.history().canUndo()) {
                String label = editorWorld.history().peekUndoLabel();
                editorWorld.history().undo();
                toasts.show("Undo: " + label, 1.2f);
            }
        }
        if (redoRequested && !redoKeyHeld) {
            if (editorWorld.history().canRedo()) {
                String label = editorWorld.history().peekRedoLabel();
                editorWorld.history().redo();
                toasts.show("Redo: " + label, 1.2f);
            }
        }
        undoKeyHeld = undoRequested;
        redoKeyHeld = redoRequested;
    }

    private void handleFrameSelectedKey(MiryHost host) {
        boolean fDown = host.isKeyDown(InputConstants.KEY_F);
        boolean edgePress = fDown && !previousFrameKeyFDown;
        previousFrameKeyFDown = fDown;
        if (!edgePress) {
            return;
        }
        editorWorld.selected().ifPresent(selected ->
                selected.getComponent(Transform3D.class).ifPresent(this::frameCameraOnTarget));
    }

    private void frameCameraOnTarget(Transform3D targetTransform) {
        Vector3f target = targetTransform.position();
        scratchRotation.identity().rotateY(cameraYawRadians).rotateX(cameraPitchRadians)
                .transform(0.0f, 0.0f, -1.0f, scratchOrbitForward);
        sceneHost.cameraTransform().setPosition(
                target.x - scratchOrbitForward.x * CAMERA_FRAME_DISTANCE,
                target.y - scratchOrbitForward.y * CAMERA_FRAME_DISTANCE,
                target.z - scratchOrbitForward.z * CAMERA_FRAME_DISTANCE);
    }

    private void applyScrollZoom() {
        float scrollY = (float) input.scrollY();
        if (scrollY == 0.0f) {
            return;
        }
        Transform3D transform = sceneHost.cameraTransform();
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        transform.translate(
                scratchForward.x * scrollY * CAMERA_SCROLL_ZOOM_STEP,
                scratchForward.y * scrollY * CAMERA_SCROLL_ZOOM_STEP,
                scratchForward.z * scrollY * CAMERA_SCROLL_ZOOM_STEP);
    }

    private void applyCameraOrientation() {
        Transform3D transform = sceneHost.cameraTransform();
        scratchRotation.identity().rotateY(cameraYawRadians).rotateX(cameraPitchRadians);
        transform.setRotation(scratchRotation);
    }

    private void applyKeyboardMovement(MiryHost host, float deltaTimeSeconds) {
        Transform3D transform = sceneHost.cameraTransform();
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        transform.rotation().transform(1.0f, 0.0f, 0.0f, scratchRight);
        scratchMovement.set(0.0f);
        if (host.isKeyDown(InputConstants.KEY_W)) scratchMovement.add(scratchForward);
        if (host.isKeyDown(InputConstants.KEY_S)) scratchMovement.sub(scratchForward);
        if (host.isKeyDown(InputConstants.KEY_D)) scratchMovement.add(scratchRight);
        if (host.isKeyDown(InputConstants.KEY_A)) scratchMovement.sub(scratchRight);
        if (host.isKeyDown(InputConstants.KEY_SPACE)) scratchMovement.add(0.0f, 1.0f, 0.0f);
        if (host.isKeyDown(InputConstants.KEY_LEFT_SHIFT)) scratchMovement.sub(0.0f, 1.0f, 0.0f);
        if (scratchMovement.lengthSquared() <= 0.0f) {
            return;
        }
        float speed = host.isKeyDown(InputConstants.KEY_LEFT_CONTROL)
                ? CAMERA_MOVE_SPEED * CAMERA_BOOST_MULTIPLIER : CAMERA_MOVE_SPEED;
        scratchMovement.normalize().mul(speed * deltaTimeSeconds);
        transform.translate(scratchMovement.x, scratchMovement.y, scratchMovement.z);
    }

    @Override
    protected void onRender(MiryHost host) {
        renderSceneIntoEditorTarget(host);
        renderEditorUiToBackBuffer(host);
        if (gameWindow != null) {
            com.miry.graphics.Texture sceneTexture = sceneHost.colorTextureForMiry();
            gameWindow.render(sceneTexture != null ? sceneTexture.id() : 0);
        }
    }

    private void renderSceneIntoEditorTarget(MiryHost host) {
        float framebufferScale = host.getFramebufferScale();
        int targetWidth = Math.max(1, Math.round(viewportPanel.lastRequestedWidth() * framebufferScale));
        int targetHeight = Math.max(1, Math.round(viewportPanel.lastRequestedHeight() * framebufferScale));
        sceneHost.ensureViewportSize(targetWidth, targetHeight);
        sceneHost.renderFrame();
    }

    private void renderEditorUiToBackBuffer(MiryHost host) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        int framebufferWidth = host.getFramebufferWidth();
        int framebufferHeight = host.getFramebufferHeight();
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        Vector4f background = ui.theme().windowBg.toVector4f();
        glClearColor(background.x, background.y, background.z, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        int windowWidth = host.getWindowWidth();
        int windowHeight = host.getWindowHeight();
        batch.begin(windowWidth, windowHeight, host.getFramebufferScale());
        dockSpace.render(batch);
        sceneTreePanel.renderOverlayMenus(batch, theme);
        inspectorPanel.renderOverlayMenus(batch, theme);
        topBarPanel.renderMenuDropdownOverlay(batch, input, theme);
        uiContext.overlay().render(batch);
        toasts.render(batch, theme, windowWidth, windowHeight);
        batch.end();
    }

    @Override
    protected void onShutdown() {
        if (gameWindow != null) {
            gameWindow.close();
            gameWindow = null;
        }
        if (editorWorld != null) {
            if (editorWorld.isPlaying()) {
                editorWorld.togglePlay();
            }
            editorWorld.playRuntime().shutdown();
        }
        sceneHost.close();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
