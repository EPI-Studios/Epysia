package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.loaders.TexturePathPrefixes;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.assets.MeshThumbnailer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.InstantiatePrefabCommand;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.importer.AssetImportPipeline;
import fr.epistudio.epysia.editor.importer.AssetImporterRegistry;
import fr.epistudio.epysia.editor.importer.GltfAssetImporter;
import fr.epistudio.epysia.editor.log.EditorConsole;
import fr.epistudio.epysia.editor.notify.ToastCenter;
import fr.epistudio.epysia.editor.play.EmbeddedPlaySession;
import fr.epistudio.epysia.editor.play.PlayController;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.preview.ShaderGraphPreviewService;
import fr.epistudio.epysia.editor.preview.VfxPreviewPanel;
import fr.epistudio.epysia.gpu.GpuLauncher;
import fr.epistudio.epysia.editor.runtime.EditorCamera;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.scene.SceneWorkspace;
import fr.epistudio.epysia.editor.scene.StarterSceneContent;
import fr.epistudio.epysia.editor.scripts.ScriptService;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.editor.shell.ImGuiShell;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphSystem;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.prefab.PrefabWriter;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import fr.epistudio.epysia.project.EditorSettings;
import java.util.List;

public final class EditorView implements FrameView {

    private static final float TOOLBAR_GROUP_SPACING = 8.0f;
    private static final float TOOLBAR_SEPARATOR_INSET = 4.0f;
    private static final float TOOLBAR_SEPARATOR_HEIGHT = 22.0f;
    private static final int TOOLBAR_SEPARATOR_COLOR = EditorStyle.rgba(255, 255, 255, 30);

    private static final float TOOLBAR_HEIGHT = 64.0f;
    private static final float STATUS_BAR_HEIGHT = 26.0f;
    private static final float SPAWN_DISTANCE = 4.0f;
    private static final float TWO_DIMENSIONAL_FRAME_MINIMUM_RADIUS = 3.0f;
    private static final String PREFABS_DIRECTORY_NAME = "prefabs";
    private static final String PREFAB_EXTENSION = ".epyprefab";
    private static final String ABOUT_POPUP_ID = "about-epysia";
    private static final String CLOSE_SCENE_POPUP_ID = "close-scene-unsaved-changes";
    private static final Set<String> COMPILED_SCRIPT_EXTENSIONS = Set.of(".java");
    private static final Set<String> SHADER_FILE_EXTENSIONS = Set.of(".glsl", ".vert", ".frag");
    private static final int HOST_WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNavFocus
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings;

    private static final String SCRIPT_TEMPLATE = """
            import fr.epistudio.epysia.EngineServices;
            import fr.epistudio.epysia.components.EpysiaComponent;
            import fr.epistudio.epysia.components.Export;
            import fr.epistudio.epysia.input.InputState;
            import fr.epistudio.epysia.scripting.Behaviour;

            @EpysiaComponent(name = "%s", category = "Scripts")
            public final class %s extends Behaviour {

                @Export(label = "Speed")
                private float speed = 1.0f;

                @Override
                public void onStart(EngineServices services) {
                }

                @Override
                public void onUpdate(InputState input, float deltaTimeSeconds) {
                }
            }
            """;

    private final Project project;
    private final ComponentRegistry componentRegistry;
    private final ProjectStore projectStore;
    private final EditorScene3DHost sceneHost;
    private final EditorCamera editorCamera;
    private final IconWidgets icons;
    private final ToastCenter toasts;
    private final ImGuiShell shell;
    private final Runnable onOpenProjectSelector;
    private final SceneSerializer serializer;
    private final SceneWorkspace workspace;
    private final EditorConsole editorConsole = new EditorConsole();
    private final PlayController playController;
    private final EmbeddedPlaySession playSession;
    private final GameObjectFactory objectFactory;
    private final AssetImportPipeline importPipeline;
    private final ScriptService scriptService;
    private final GizmoState gizmoState = new GizmoState();
    private final DockLayout dockLayout = new DockLayout();
    private final HierarchyView hierarchyView;
    private final InspectorView inspectorView;
    private final ViewportView viewportView;
    private final ConsoleView consoleView;
    private final AssetBrowserView assetBrowserView;
    private final ScriptEditorView scriptEditorView;
    private final PanelTimings panelTimings = new PanelTimings();
    private final ProfilerView profilerView;
    private final LightingView lightingView;
    private final SpriteEditorWindow spriteEditorWindow;
    private final TileBrush tileBrush = new TileBrush();
    private final TilePalettePanel tilePalettePanel;
    private final TilemapDockView tilemapDockView;
    private final GraphEditorView graphEditorView;
    private final SettingsDialog settingsDialog;
    private final PostEffectsSection settingsPostEffectsSection;
    private final MeshBakeDialog meshBakeDialog;
    private final ExportGameDialog exportGameDialog;
    private final NameDialog nameDialog = new NameDialog("##editor-name-dialog");
    private final ThumbnailCache thumbnailCache;
    private final ImagePreviewTexture imagePreview;
    private final MeshThumbnailer meshThumbnailer;
    private final ShaderGraphPreviewService shaderGraphPreviews;
    private final VfxPreviewPanel vfxPreviewPanel;
    private EditorPreferences preferences;
    private SceneDocument playedDocument;
    private SceneDocument pendingCloseDocument;
    private boolean previousPlayRunning;
    private boolean aboutRequested;
    private boolean closeSceneRequested;
    private boolean graphRegistryInjected;
    private float secondsSinceAutosave;

    public EditorView(Project project, ComponentRegistry componentRegistry, ProjectStore projectStore,
                      EditorScene3DHost sceneHost, EditorCamera editorCamera, IconWidgets icons,
                      ToastCenter toasts, ImGuiShell shell, Runnable onOpenProjectSelector) {
        this.project = project;
        this.componentRegistry = componentRegistry;
        this.projectStore = projectStore;
        this.sceneHost = sceneHost;
        this.editorCamera = editorCamera;
        this.icons = icons;
        this.toasts = toasts;
        this.shell = shell;
        this.onOpenProjectSelector = onOpenProjectSelector;
        this.serializer = new SceneSerializer(componentRegistry);
        this.workspace = new SceneWorkspace(project, sceneHost, serializer, componentRegistry, toasts);
        this.preferences = EditorPreferences.load(EditorPreferences.defaultFile());
        this.playController = new PlayController(project, () -> workspace.active().scene(),
                serializer, sceneHost.engine());
        this.thumbnailCache = new ThumbnailCache(sceneHost.backend());
        this.imagePreview = new ImagePreviewTexture(sceneHost.backend());
        this.meshThumbnailer = new MeshThumbnailer();
        sceneHost.engine().setLogger(editorConsole.logger());
        Supplier<SceneDocument> active = workspace::active;
        this.playSession = new EmbeddedPlaySession(sceneHost, serializer, project, projectStore,
                active, toasts, editorConsole);
        this.objectFactory = new GameObjectFactory(active, sceneHost.engine());
        this.importPipeline = new AssetImportPipeline(buildImporterRegistry(componentRegistry));
        this.scriptEditorView = new ScriptEditorView(componentRegistry, toasts, this::onScriptFileSaved);
        this.shaderGraphPreviews = new ShaderGraphPreviewService(sceneHost.window(), sceneHost.backend());
        this.vfxPreviewPanel = new VfxPreviewPanel(sceneHost.window(), sceneHost.backend());
        this.graphEditorView = new GraphEditorView(componentRegistry, toasts, active,
                thumbnailCache, this::onShaderGraphGenerated, shaderGraphPreviews, vfxPreviewPanel,
                new AssetPicker(project), () -> preferences.shaderNodePreviewsEnabled(),
                this::onShaderNodePreviewsToggled, this::projectActionNames);
        this.scriptService = new ScriptService(project, componentRegistry, serializer, workspace,
                this::onScriptMessage, sceneHost::applyProjectRenderSetups);
        this.tilePalettePanel = new TilePalettePanel(sceneHost.backend(), sceneHost.engine(), active, tileBrush);
        this.viewportView = new ViewportView(sceneHost, editorCamera, active, gizmoState,
                shell.windowHandle(), playSession, icons, objectFactory, importPipeline, tilePalettePanel);
        this.hierarchyView = new HierarchyView(active, componentRegistry, toasts, icons, this::saveAsPrefab,
                viewportView::frameObject, objectFactory, this::spawnPositionInFront);
        this.spriteEditorWindow = new SpriteEditorWindow(
                new ImagePreviewTexture(sceneHost.backend()), this::onAtlasSaved);
        this.tilemapDockView = new TilemapDockView(active, sceneHost.engine(), icons, tilePalettePanel,
                viewportView::enablePainting, editorCamera::twoDimensional);
        this.inspectorView = new InspectorView(active, componentRegistry, toasts, icons,
                new AssetPicker(project), thumbnailCache, project, this::createScriptAndAttach,
                graphEditorView::open, this::selectedBrowserAssetPath,
                new AtlasInspectorSection(spriteEditorWindow::open),
                new TextureInspectorSection(imagePreview, this::onTextureFilterChanged),
                tilemapDockView::focus);
        this.consoleView = new ConsoleView(playController, editorConsole, project.scriptsDirectory(),
                location -> scriptEditorView.open(location.file(), location.line()));
        this.meshBakeDialog = new MeshBakeDialog(toasts, this::onMeshBaked);
        this.assetBrowserView = new AssetBrowserView(project, toasts, icons, thumbnailCache, meshThumbnailer,
                scriptEditorView::open, meshBakeDialog::openFor,
                this::instantiatePrefabAtOrigin, this::openScenePath, this::attachScriptToSelected,
                graphEditorView::open, spriteEditorWindow::open, importPipeline);
        this.settingsDialog = new SettingsDialog(this::onSettingsSaved, this::onPreferencesSaved,
                this::onViewportTuningChanged);
        this.settingsPostEffectsSection = new PostEffectsSection(project, thumbnailCache);
        this.profilerView = new ProfilerView(sceneHost, shell, active, viewportView, panelTimings);
        this.lightingView = new LightingView(sceneHost, active, project.rootDirectory());
        this.exportGameDialog = new ExportGameDialog(project, toasts);
        shell.setFileDropHandler(assetBrowserView::importExternalFiles);
        finishSetup();
    }

    private Optional<Path> selectedBrowserAssetPath() {
        return assetBrowserView == null ? Optional.empty() : assetBrowserView.selectedEntryPath();
    }

    private static AssetImporterRegistry buildImporterRegistry(ComponentRegistry componentRegistry) {
        AssetImporterRegistry registry = new AssetImporterRegistry();
        registry.register(new GltfAssetImporter(componentRegistry));
        return registry;
    }

    private void finishSetup() {
        applyPreferences();
        scriptService.reload();
        assetBrowserView.sweepProjectForImports();
        if (Files.isRegularFile(project.defaultScenePath())) {
            workspace.open(project.defaultScenePath());
        } else {
            workspace.create(Project.DEFAULT_SCENE_NAME, StarterSceneContent::populate);
        }
        applyViewportModeForScene();
        if (!Files.isRegularFile(Path.of(ImGui.getIO().getIniFilename()))) {
            dockLayout.requestDefaultLayout();
        }
    }

    private void applyPreferences() {
        editorCamera.setMoveSpeed(preferences.cameraSpeed());
        editorCamera.setBoostMultiplier(preferences.cameraBoost());
        editorCamera.setLookSensitivity(preferences.lookSensitivity());
        editorCamera.setInvertLookY(preferences.invertLookY());
        editorCamera.setClipPlanes(preferences.sceneNear(), preferences.sceneFar());
        editorCamera.setFieldOfViewDegrees(preferences.sceneFieldOfView());
        editorCamera.setTwoDimensional(preferences.viewport2DMode());
        viewportView.setShowGrid(preferences.gridVisible());
        gizmoState.setSnapEnabled(preferences.snapEnabled());
        viewportView.setOverlayThicknessMultiplier(preferences.overlayThickness());
        viewportView.setGridFadeDistance(preferences.gridFadeDistance());
    }

    private EditorHistory history() {
        return workspace.active().history();
    }

    @Override
    public void render(float deltaSeconds) {
        requestRedrawOnInteraction();
        pollBackgroundState(deltaSeconds);
        renderMainMenuBar();
        renderHostWindow();
        renderPanels(deltaSeconds);
        renderDialogs();
        handleGlobalShortcuts();
        playSession.frame(deltaSeconds);
    }

    private void requestRedrawOnInteraction() {
        if (pointerMoved() || ImGui.isAnyMouseDown() || ImGui.isAnyItemActive()
                || ImGui.getIO().getMouseWheel() != 0.0f
                || ImGui.getIO().getMouseWheelH() != 0.0f
                || ImGui.getIO().getWantCaptureKeyboard()) {
            sceneHost.requestViewportRedraw();
        }
    }

    private boolean pointerMoved() {
        return ImGui.getIO().getMouseDeltaX() != 0.0f || ImGui.getIO().getMouseDeltaY() != 0.0f;
    }

    private void pollBackgroundState(float deltaSeconds) {
        injectGraphComponentRegistryOnce();
        playController.pollExit();
        scriptService.poll(System.currentTimeMillis());
        boolean runningNow = playController.isRunning();
        if (previousPlayRunning && !runningNow && playedDocument != null) {
            playedDocument.history().clear();
            playedDocument = null;
        }
        previousPlayRunning = runningNow;
        runAutosave(deltaSeconds);
    }

    private void injectGraphComponentRegistryOnce() {
        if (graphRegistryInjected) {
            return;
        }
        GraphSystem graphSystem = sceneHost.engine().systems().get(GraphSystem.class);
        if (graphSystem != null) {
            graphSystem.setComponentRegistry(componentRegistry);
            graphRegistryInjected = true;
        }
    }

    private void runAutosave(float deltaSeconds) {
        if (!preferences.autosaveEnabled()) {
            return;
        }
        secondsSinceAutosave += deltaSeconds;
        if (secondsSinceAutosave < preferences.autosaveIntervalSeconds()) {
            return;
        }
        secondsSinceAutosave = 0.0f;
        if (workspace.active().isDirty() && !playController.isRunning() && !playSession.isActive()) {
            workspace.save(workspace.active());
        }
    }

    private void renderHostWindow() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY(), ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.begin("##editor-host", HOST_WINDOW_FLAGS);
        ImGui.popStyleVar(2);
        renderToolbarStrip();
        dockLayout.buildIfRequested(viewport);
        ImGui.dockSpace(dockLayout.dockspaceId(), 0.0f, -STATUS_BAR_HEIGHT);
        renderStatusBar();
        ImGui.end();
    }

    private void renderPanels(float deltaSeconds) {
        boolean editingBlocked = playSession.isActive();
        panelTimings.beginFrame();
        ImGui.beginDisabled(editingBlocked);
        panelTimings.measure("Hierarchy", hierarchyView::render);
        panelTimings.measure("Inspector", inspectorView::render);
        ImGui.endDisabled();
        panelTimings.measure("Viewport", () -> viewportView.render(deltaSeconds));
        panelTimings.measure("Console", consoleView::render);
        panelTimings.measure("Assets", assetBrowserView::render);
        panelTimings.measure("Scripts", scriptEditorView::render);
        panelTimings.measure("Graphs", graphEditorView::render);
        panelTimings.measure("Profiler", profilerView::render);
        panelTimings.measure("Lighting", lightingView::render);
        panelTimings.measure("Sprites", spriteEditorWindow::render);
        panelTimings.measure("Tilemap", tilemapDockView::render);
    }

    private void renderDialogs() {
        settingsDialog.render();
        meshBakeDialog.render();
        exportGameDialog.render();
        nameDialog.render();
        renderAboutPopup();
        renderCloseScenePopup();
    }

    private void renderMainMenuBar() {
        if (!ImGui.beginMainMenuBar()) {
            return;
        }
        renderFileMenu();
        renderEditMenu();
        renderGameObjectMenu();
        renderWindowMenu();
        renderHelpMenu();
        ImGui.endMainMenuBar();
    }

    private void renderFileMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE, "menu-file"))) {
            return;
        }
        renderFileSceneItems();
        ImGui.separator();
        renderFileScriptItems();
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_EXPORT_GAME, "menu-file-export-game"))) {
            exportGameDialog.open(workspace.active().name());
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_OPEN_PROJECT,
                "menu-file-new-open-project"))) {
            onOpenProjectSelector.run();
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_EXIT, "menu-file-exit"))) {
            shell.requestClose();
        }
        ImGui.endMenu();
    }

    private void renderFileSceneItems() {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_SCENE, "menu-file-new-scene"))) {
            workspace.create();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_OPEN_SCENE, "menu-file-open-scene"))) {
            openSceneDialog();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_SAVE, "menu-file-save"), "Ctrl+S")) {
            saveScene();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_SAVE_AS, "menu-file-save-as"))) {
            saveSceneAs();
        }
    }

    private void renderFileScriptItems() {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_SCRIPT, "menu-file-new-script"))) {
            createNewScript();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_RELOAD_SCRIPTS,
                "menu-file-reload-scripts"))) {
            reloadScripts();
        }
    }

    private void renderEditMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT, "menu-edit"))) {
            return;
        }
        EditorHistory history = history();
        String undoLabel = history.undoLabel()
                .map(label -> I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_UNDO_ACTION,
                        "menu-edit-undo", label))
                .orElse(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_UNDO, "menu-edit-undo"));
        String redoLabel = history.redoLabel()
                .map(label -> I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_REDO_ACTION,
                        "menu-edit-redo", label))
                .orElse(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_REDO, "menu-edit-redo"));
        if (ImGui.menuItem(undoLabel, "Ctrl+Z", false, history.canUndo())) {
            history.undo();
        }
        if (ImGui.menuItem(redoLabel, "Ctrl+Y", false, history.canRedo())) {
            history.redo();
        }
        ImGui.separator();
        renderEditSelectionItems();
        ImGui.endMenu();
    }

    private void renderEditSelectionItems() {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_DUPLICATE,
                "menu-edit-duplicate"), "Ctrl+D")) {
            hierarchyView.duplicateSelected();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_DELETE, "menu-edit-delete"), "Del")) {
            hierarchyView.askDeleteSelected();
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_SETTINGS, "menu-edit-settings"))) {
            openSettings();
        }
    }

    private void renderGameObjectMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT, "menu-gameobject"))) {
            return;
        }
        ImGui.beginDisabled(playSession.isActive());
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CREATE_EMPTY,
                "menu-gameobject-create-empty"))) {
            objectFactory.createEmpty(spawnPositionInFront());
        }
        ImGui.separator();
        renderPrimitiveItems();
        ImGui.separator();
        renderLightAndCameraItems();
        ImGui.endDisabled();
        ImGui.endMenu();
    }

    private void renderPrimitiveItems() {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CUBE, "menu-gameobject-cube"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CUBE, spawnPositionInFront());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_PLANE, "menu-gameobject-plane"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.PLANE, spawnPositionInFront());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CAPSULE,
                "menu-gameobject-capsule"))) {
            objectFactory.createPrimitive(GameObjectFactory.Primitive.CAPSULE, spawnPositionInFront());
        }
        ImGui.separator();
        if (ImGui.menuItem("2D Sprite")) {
            objectFactory.createSprite(spawnPositionInFront());
        }
        if (ImGui.menuItem("Tilemap")) {
            objectFactory.createTilemap(spawnPositionInFront());
        }
    }

    private void renderLightAndCameraItems() {
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_DIRECTIONAL_LIGHT,
                "menu-gameobject-directional-light"))) {
            objectFactory.createDirectionalLight(spawnPositionInFront());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_POINT_LIGHT,
                "menu-gameobject-point-light"))) {
            objectFactory.createPointLight(spawnPositionInFront());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_SPOT_LIGHT,
                "menu-gameobject-spot-light"))) {
            objectFactory.createSpotLight(spawnPositionInFront());
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_GAMEOBJECT_CAMERA, "menu-gameobject-camera"))) {
            objectFactory.createCamera(spawnPositionInFront());
        }
    }

    private Vector3f spawnPositionInFront() {
        Vector3f position = editorCamera.camera().position(new Vector3f());
        Vector3f forward = editorCamera.transform().rotation().transform(new Vector3f(0.0f, 0.0f, -1.0f));
        return position.add(forward.mul(SPAWN_DISTANCE));
    }

    private void renderWindowMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW, "menu-window"))) {
            return;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_RESET_LAYOUT,
                "menu-window-reset-layout"))) {
            dockLayout.requestDefaultLayout();
        }
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_GRID, "menu-window-grid"),
                "", viewportView.showGrid())) {
            toggleGridPreference();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_COLLIDER_WIREFRAMES,
                "menu-window-collider-wireframes"), "", viewportView.showColliderWireframes())) {
            viewportView.setShowColliderWireframes(!viewportView.showColliderWireframes());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_PROFILER,
                "menu-window-profiler"), "", profilerView.isVisible())) {
            profilerView.setVisible(!profilerView.isVisible());
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_LIGHTING,
                "menu-window-lighting"), "", lightingView.isVisible())) {
            lightingView.setVisible(!lightingView.isVisible());
        }
        if (ImGui.menuItem(TilemapDockView.WINDOW_TITLE, "", tilemapDockView.isVisible())) {
            tilemapDockView.setVisible(!tilemapDockView.isVisible());
        }
        if (ImGui.menuItem(SpriteEditorWindow.WINDOW_TITLE, "", spriteEditorWindow.isVisible())) {
            spriteEditorWindow.setVisible(!spriteEditorWindow.isVisible());
        }
        ImGui.endMenu();
    }

    private void renderHelpMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_HELP, "menu-help"))) {
            return;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_ABOUT_TITLE, "menu-help-about"))) {
            aboutRequested = true;
        }
        ImGui.endMenu();
    }

    private void renderAboutPopup() {
        if (aboutRequested) {
            ImGui.openPopup(aboutPopupLabel());
            aboutRequested = false;
        }
        if (!ImGui.beginPopupModal(aboutPopupLabel(), ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_ABOUT_ENGINE,
                ProjectStore.CURRENT_ENGINE_VERSION));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_ABOUT_PROJECT,
                project.name(), project.engineVersion()));
        ImGui.separator();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_CLOSE, "about-close"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private static String aboutPopupLabel() {
        return I18n.label(TextKey.EDITOR_EDITOR_VIEW_ABOUT_TITLE, ABOUT_POPUP_ID);
    }

    private void renderToolbarStrip() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorStyle.WINDOW_PADDING, EditorStyle.FRAME_PADDING_Y);
        ImGui.beginChild("##toolbar", 0.0f, TOOLBAR_HEIGHT, false);
        renderToolButtons();
        renderSceneTabs();
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.separator();
    }

    private void renderToolButtons() {
        renderGizmoToolButtons();
        renderToolbarSeparator();
        renderToggleButtons();
        renderPlayControls();
    }

    private static void renderToolbarSeparator() {
        ImGui.sameLine(0.0f, TOOLBAR_GROUP_SPACING);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addLine(x, y + TOOLBAR_SEPARATOR_INSET,
                x, y + TOOLBAR_SEPARATOR_HEIGHT, TOOLBAR_SEPARATOR_COLOR);
        ImGui.dummy(1.0f, TOOLBAR_SEPARATOR_HEIGHT);
        ImGui.sameLine(0.0f, TOOLBAR_GROUP_SPACING);
    }

    private void renderGizmoToolButtons() {
        renderToolButton("tool-select", EditorIcon.TOOL_SELECT, GizmoState.Tool.SELECT,
                TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SELECT);
        ImGui.sameLine();
        renderToolButton("tool-move", EditorIcon.TOOL_MOVE, GizmoState.Tool.TRANSLATE,
                TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_MOVE);
        ImGui.sameLine();
        renderToolButton("tool-rotate", EditorIcon.TOOL_ROTATE, GizmoState.Tool.ROTATE,
                TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_ROTATE);
        ImGui.sameLine();
        renderToolButton("tool-scale", EditorIcon.TOOL_SCALE, GizmoState.Tool.SCALE,
                TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SCALE);
        ImGui.sameLine();
        TextKey spaceKey = gizmoState.worldSpace()
                ? TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_WORLD
                : TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_LOCAL;
        if (ImGui.button(I18n.label(spaceKey, "toolbar-gizmo-space"))) {
            gizmoState.toggleSpace();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_TOGGLE_GIZMO_SPACE);
        ImGui.sameLine();
        renderTwoDimensionalToggle();
        ImGui.sameLine();
        renderPaintToggle();
        ImGui.sameLine();
        renderTileCollisionToggle();
    }

    private void renderTileCollisionToggle() {
        boolean active = viewportView.showTileCollision();
        if (icons.toggleButton("tool-tile-collision", EditorIcon.COLLISION_SHAPE_3D,
                EditorStyle.ICON_SIZE_TOOLBAR, active)) {
            viewportView.setShowTileCollision(!active);
        }
        tooltip("Show tile collision shapes over the map (2D view)");
    }

    private void renderPaintToggle() {
        boolean active = viewportView.paintEnabled();
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        }
        boolean clicked = ImGui.button("Paint");
        if (active) {
            ImGui.popStyleColor();
        }
        tooltip("Paint tiles onto the selected tilemap (2D view)");
        if (clicked) {
            viewportView.setPaintEnabled(!active);
        }
    }

    private void renderTwoDimensionalToggle() {
        boolean active = editorCamera.twoDimensional();
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        }
        boolean clicked = ImGui.button("2D");
        if (active) {
            ImGui.popStyleColor();
        }
        tooltip("Toggle 2D view (orthographic front view)");
        if (clicked) {
            toggleTwoDimensionalPreference();
        }
    }

    private void toggleTwoDimensionalPreference() {
        editorCamera.setTwoDimensional(!editorCamera.twoDimensional());
        preferences = preferences.withViewport2DMode(editorCamera.twoDimensional());
        persistPreferences();
    }

    private void renderToolButton(String id, EditorIcon icon, GizmoState.Tool tool, TextKey tooltipKey) {
        if (icons.toggleButton(id, icon, EditorStyle.ICON_SIZE_TOOLBAR, gizmoState.tool() == tool)) {
            gizmoState.setTool(tool);
        }
        tooltip(tooltipKey);
    }

    private void renderToggleButtons() {
        if (icons.toggleButton("toolbar-snap", EditorIcon.SNAP, EditorStyle.ICON_SIZE_TOOLBAR,
                gizmoState.snapEnabled())) {
            toggleSnapPreference();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SNAP);
        ImGui.sameLine();
        if (icons.toggleButton("toolbar-grid", EditorIcon.GRID, EditorStyle.ICON_SIZE_TOOLBAR,
                viewportView.showGrid())) {
            toggleGridPreference();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_GRID);
        ImGui.sameLine();
        renderSaveButton();
    }

    private void renderSaveButton() {
        if (icons.iconButton("toolbar-save", EditorIcon.SAVE, EditorStyle.ICON_SIZE_TOOLBAR)) {
            saveScene();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SAVE_SCENE);
    }

    private void renderPlayControls() {
        float center = ImGui.getWindowWidth() * 0.5f - EditorStyle.ICON_SIZE_TOOLBAR * 4.0f;
        ImGui.sameLine(center);
        renderEmbeddedPlayButtons();
        ImGui.sameLine();
        renderRunGameButton();
    }

    private void renderEmbeddedPlayButtons() {
        boolean active = playSession.isActive();
        ImGui.beginDisabled(active);
        if (icons.iconButton("toolbar-play", EditorIcon.PLAY, EditorStyle.ICON_SIZE_TOOLBAR)) {
            playSession.start();
        }
        ImGui.endDisabled();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PLAY);
        ImGui.sameLine();
        renderPauseStepStopButtons(active);
    }

    private void renderPauseStepStopButtons(boolean active) {
        ImGui.beginDisabled(!active);
        if (icons.toggleButton("toolbar-pause", EditorIcon.PAUSE, EditorStyle.ICON_SIZE_TOOLBAR,
                playSession.state() == EmbeddedPlaySession.State.PAUSED)) {
            playSession.togglePause();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PAUSE);
        ImGui.sameLine();
        ImGui.beginDisabled(playSession.state() != EmbeddedPlaySession.State.PAUSED);
        if (icons.iconButton("toolbar-step", EditorIcon.REDO, EditorStyle.ICON_SIZE_TOOLBAR)) {
            playSession.step();
        }
        ImGui.endDisabled();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_STEP);
        ImGui.sameLine();
        if (icons.iconButton("toolbar-stop", EditorIcon.STOP, EditorStyle.ICON_SIZE_TOOLBAR)) {
            playSession.stop();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_STOP);
        ImGui.endDisabled();
    }

    private void renderRunGameButton() {
        renderToolbarSeparator();
        boolean subprocessRunning = playController.isRunning();
        ImGui.beginDisabled(subprocessRunning || playSession.isActive());
        if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_RUN_GAME, "toolbar-run-game"))) {
            startPlay();
        }
        ImGui.endDisabled();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_RUN_GAME_TOOLTIP);
        if (subprocessRunning) {
            ImGui.sameLine();
            if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_KILL, "toolbar-kill-game"))) {
                playController.stop();
            }
            tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_KILL_TOOLTIP);
        }
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }

    private void tooltip(TextKey key) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(key));
        }
    }

    private void renderSceneTabs() {
        if (playSession.isActive()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SCENE_PLAYING,
                    workspace.active().name()));
            return;
        }
        if (!ImGui.beginTabBar("##scene-tabs", ImGuiTabBarFlags.AutoSelectNewTabs)) {
            return;
        }
        for (SceneDocument document : List.copyOf(workspace.documents())) {
            renderSceneTab(document);
        }
        if (ImGui.tabItemButton("+", ImGuiTabItemFlags.Trailing)) {
            workspace.create();
        }
        ImGui.endTabBar();
    }

    private void renderSceneTab(SceneDocument document) {
        ImBoolean keepOpen = new ImBoolean(true);
        int flags = document.isDirty() ? ImGuiTabItemFlags.UnsavedDocument : ImGuiTabItemFlags.None;
        boolean selected = ImGui.beginTabItem(document.name() + "##" + document.filePath(), keepOpen, flags);
        if (selected) {
            activateDocument(document);
            ImGui.endTabItem();
        }
        if (!keepOpen.get()) {
            requestCloseDocument(document);
        }
    }

    private void activateDocument(SceneDocument document) {
        int index = workspace.documents().indexOf(document);
        if (index >= 0 && index != workspace.activeIndex()) {
            workspace.switchTo(index);
        }
    }

    private void requestCloseDocument(SceneDocument document) {
        if (!document.isDirty()) {
            workspace.close(document);
            return;
        }
        pendingCloseDocument = document;
        closeSceneRequested = true;
    }

    private void renderCloseScenePopup() {
        if (closeSceneRequested) {
            ImGui.openPopup(closeScenePopupLabel());
            closeSceneRequested = false;
        }
        if (!ImGui.beginPopupModal(closeScenePopupLabel(), ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_UNSAVED_CHANGES_MESSAGE,
                pendingCloseDocument.name()));
        ImGui.separator();
        renderCloseSceneButtons();
        ImGui.endPopup();
    }

    private static String closeScenePopupLabel() {
        return I18n.label(TextKey.EDITOR_EDITOR_VIEW_UNSAVED_CHANGES_TITLE, CLOSE_SCENE_POPUP_ID);
    }

    private void renderCloseSceneButtons() {
        if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_UNSAVED_CHANGES_SAVE,
                "close-scene-save"))) {
            workspace.save(pendingCloseDocument);
            workspace.close(pendingCloseDocument);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_UNSAVED_CHANGES_DISCARD,
                "close-scene-discard"))) {
            workspace.close(pendingCloseDocument);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EDITOR_VIEW_UNSAVED_CHANGES_CANCEL,
                "close-scene-cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private void renderStatusBar() {
        ImGui.beginChild("##status-bar", 0.0f, STATUS_BAR_HEIGHT, false);
        ImGui.setCursorPosX(EditorStyle.WINDOW_PADDING);
        renderPlayState();
        ImGui.sameLine();
        ImGui.textDisabled(objectCountLabel());
        renderLastLogLine();
        renderFramerate();
        ImGui.endChild();
    }

    private void renderPlayState() {
        if (playSession.state() == EmbeddedPlaySession.State.PLAYING) {
            ImGui.textColored(EditorStyle.COLOR_ACCENT, I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_PLAYING));
        } else if (playSession.state() == EmbeddedPlaySession.State.PAUSED) {
            ImGui.textColored(EditorStyle.COLOR_WARNING, I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_PAUSED));
        } else if (playController.isRunning()) {
            ImGui.textColored(EditorStyle.COLOR_ACCENT,
                    I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_GAME_RUNNING));
        } else {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_READY));
        }
        ImGui.sameLine();
        ImGui.textDisabled(contextHint());
    }

    private String contextHint() {
        if (playSession.isActive()) {
            return I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_PLAY_CONTEXT);
        }
        if (viewportView.isHovered() && editorCamera.twoDimensional()) {
            return "RMB/MMB Pan  |  Scroll Zoom  |  F Frame  |  Ctrl+D Duplicate";
        }
        if (viewportView.isHovered()) {
            return I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_VIEWPORT_CONTEXT);
        }
        return "";
    }

    private void renderLastLogLine() {
        Optional<String> lastLine = consoleView.lastLine();
        if (lastLine.isPresent()) {
            ImGui.sameLine();
            ImGui.textDisabled(truncate(lastLine.get()));
        }
    }

    private void renderFramerate() {
        String fps = I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_FPS,
                String.format(Locale.ROOT, "%.0f", ImGui.getIO().getFramerate()));
        ImGui.sameLine(ImGui.getWindowWidth() - ImGui.calcTextSize(fps).x - EditorStyle.WINDOW_PADDING);
        ImGui.textColored(EditorStyle.COLOR_ACCENT, fps);
    }

    private String objectCountLabel() {
        int count = workspace.active().scene().gameObjects().size();
        String label = I18n.translate(count == 1
                ? TextKey.EDITOR_EDITOR_VIEW_STATUS_OBJECT_COUNT_ONE
                : TextKey.EDITOR_EDITOR_VIEW_STATUS_OBJECT_COUNT_MANY, count);
        int selectedCount = workspace.active().selection().count();
        if (selectedCount > 1) {
            return label + "  |  " + I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_SELECTED_COUNT,
                    selectedCount);
        }
        return workspace.active().selection().get()
                .map(primary -> label + "  |  " + primary.name())
                .orElse(label);
    }

    private static String truncate(String message) {
        return message.length() <= 60 ? message : message.substring(0, 60) + "…";
    }

    private void handleGlobalShortcuts() {
        if (rightMouseHeld() || ImGui.isAnyItemActive()) {
            return;
        }
        if (ImGui.getIO().getKeyCtrl()) {
            handleControlShortcutsUnlessScriptEditorOwnsThem();
        } else if (!ImGui.getIO().getWantTextInput() && viewportView.acceptsToolHotkeys()) {
            handleToolHotkeys();
        }
    }

    private void handleControlShortcutsUnlessScriptEditorOwnsThem() {
        if (!scriptEditorView.isFocused()) {
            handleControlShortcuts();
        }
    }

    private void handleControlShortcuts() {
        if (ImGui.isKeyPressed(ImGuiKey.P)) {
            togglePlaySession();
        }
        if (playSession.isActive()) {
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.S)) {
            saveScene();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Z)) {
            applyUndoRedoShortcut();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Y)) {
            history().redo();
        }
        if (ImGui.isKeyPressed(ImGuiKey.D)) {
            hierarchyView.duplicateSelected();
        }
    }

    private void togglePlaySession() {
        if (playSession.isActive()) {
            playSession.stop();
        } else {
            playSession.start();
        }
    }

    private void applyUndoRedoShortcut() {
        if (ImGui.getIO().getKeyShift()) {
            history().redo();
        } else {
            history().undo();
        }
    }

    private void handleToolHotkeys() {
        if (ImGui.isKeyPressed(ImGuiKey.Q, false)) {
            gizmoState.setTool(GizmoState.Tool.SELECT);
        }
        if (ImGui.isKeyPressed(ImGuiKey.W, false)) {
            gizmoState.setTool(GizmoState.Tool.TRANSLATE);
        }
        if (ImGui.isKeyPressed(ImGuiKey.R, false)) {
            gizmoState.setTool(GizmoState.Tool.ROTATE);
        }
        if (ImGui.isKeyPressed(ImGuiKey.S, false)) {
            gizmoState.setTool(GizmoState.Tool.SCALE);
        }
        if (ImGui.isKeyPressed(ImGuiKey.X, false)) {
            gizmoState.toggleSpace();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Space, false)) {
            gizmoState.toggleAlternateTool();
        }
    }

    private boolean rightMouseHeld() {
        return GLFW.glfwGetMouseButton(shell.windowHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private void openSceneDialog() {
        FileDialogs.pickFile(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_OPEN_SCENE_DIALOG_TITLE),
                        project.scenesDirectory(), "*" + Project.SCENE_EXTENSION,
                        I18n.translate(TextKey.EDITOR_EDITOR_VIEW_OPEN_SCENE_DIALOG_FILTER))
                .ifPresent(this::openScenePath);
    }

    private void openScenePath(Path path) {
        if (!path.getFileName().toString().endsWith(Project.SCENE_EXTENSION)) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_NOT_SCENE_FILE));
            return;
        }
        workspace.open(path);
        applyViewportModeForScene();
    }

    private void applyViewportModeForScene() {
        Scene scene = workspace.active().scene();
        if (!isPredominantlyTwoDimensional(scene)) {
            editorCamera.setTwoDimensional(false);
            return;
        }
        editorCamera.setTwoDimensional(true);
        frameTwoDimensionalContent(scene);
    }

    private static boolean isPredominantlyTwoDimensional(Scene scene) {
        int twoDimensionalCount = 0;
        int meshCount = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            if (hasTwoDimensionalRenderer(gameObject)) {
                twoDimensionalCount++;
            }
            if (gameObject.getComponent(MeshRenderer.class).isPresent()) {
                meshCount++;
            }
        }
        return twoDimensionalCount > 0 && meshCount < twoDimensionalCount;
    }

    private static boolean hasTwoDimensionalRenderer(GameObject gameObject) {
        return gameObject.getComponent(SpriteRenderer.class).isPresent()
                || gameObject.getComponent(TilemapRenderer.class).isPresent();
    }

    private void frameTwoDimensionalContent(Scene scene) {
        Vector2f minimum = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        Vector2f maximum = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
        if (!accumulateTwoDimensionalBounds(scene, minimum, maximum)) {
            return;
        }
        Vector3f center = new Vector3f((minimum.x + maximum.x) * 0.5f, (minimum.y + maximum.y) * 0.5f, 0.0f);
        float radius = Math.max(TWO_DIMENSIONAL_FRAME_MINIMUM_RADIUS,
                0.5f * Math.max(maximum.x - minimum.x, maximum.y - minimum.y));
        editorCamera.frame(center, radius);
    }

    private static boolean accumulateTwoDimensionalBounds(Scene scene, Vector2f minimum, Vector2f maximum) {
        boolean found = false;
        for (GameObject gameObject : scene.gameObjects()) {
            if (!hasTwoDimensionalRenderer(gameObject)) {
                continue;
            }
            Optional<Transform2D> transform = gameObject.getComponent(Transform2D.class);
            if (transform.isPresent()) {
                expandBounds(minimum, maximum, transform.get());
                found = true;
            }
        }
        return found;
    }

    private static void expandBounds(Vector2f minimum, Vector2f maximum, Transform2D transform) {
        float halfWidth = Math.abs(transform.scale().x) * 0.5f;
        float halfHeight = Math.abs(transform.scale().y) * 0.5f;
        Vector2f position = transform.position();
        minimum.set(Math.min(minimum.x, position.x - halfWidth), Math.min(minimum.y, position.y - halfHeight));
        maximum.set(Math.max(maximum.x, position.x + halfWidth), Math.max(maximum.y, position.y + halfHeight));
    }

    private void saveScene() {
        if (playSession.isActive()) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_STOP_PLAY_MODE_TO_SAVE));
            return;
        }
        int tilemaps = tilePalettePanel.saveDirtyTilemaps(workspace.active().scene());
        workspace.save(workspace.active());
        secondsSinceAutosave = 0.0f;
        if (tilemaps > 0) {
            toasts.show("Saved scene and " + tilemaps + " tilemap(s)");
        }
    }

    private void saveSceneAs() {
        nameDialog.open(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SAVE_SCENE_AS_TITLE),
                workspace.active().name(), this::saveSceneAsNamed);
    }

    private void saveSceneAsNamed(String name) {
        SceneDocument document = workspace.active();
        Path target = project.scenesDirectory().resolve(name + Project.SCENE_EXTENSION);
        document.renameTo(target, name);
        workspace.save(document);
    }

    private void saveAsPrefab(GameObject root) {
        nameDialog.open(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SAVE_PREFAB_TITLE),
                root.name(), name -> writePrefab(root, name));
    }

    private void writePrefab(GameObject root, String name) {
        Path directory = project.rootDirectory().resolve(PREFABS_DIRECTORY_NAME);
        Path target = directory.resolve(name + PREFAB_EXTENSION);
        try {
            Files.createDirectories(directory);
            new PrefabWriter(componentRegistry).write(root, target);
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_PREFAB_SAVED, target.getFileName()));
            assetBrowserView.refreshAssets();
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_PREFAB_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    private void instantiatePrefabAtOrigin(Path prefabPath) {
        history().execute(new InstantiatePrefabCommand(prefabPath, new Vector3f()));
    }

    private void createNewScript() {
        try {
            Files.createDirectories(project.scriptsDirectory());
            Path target = nextScriptFile(project.scriptsDirectory());
            String className = target.getFileName().toString().replace(".java", "");
            Files.writeString(target, String.format(SCRIPT_TEMPLATE, className, className));
            scriptEditorView.open(target);
            reloadScripts();
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_CREATION_FAILED,
                    error.getMessage()));
        }
    }

    private static Path nextScriptFile(Path scriptsDirectory) {
        String baseName = I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SCRIPT_DEFAULT_NAME);
        Path target = scriptsDirectory.resolve(baseName + ".java");
        int suffix = 2;
        while (Files.exists(target)) {
            target = scriptsDirectory.resolve(baseName + suffix + ".java");
            suffix++;
        }
        return target;
    }

    private void createScriptAndAttach(String className, GameObject target) {
        try {
            Files.createDirectories(project.scriptsDirectory());
            Path file = project.scriptsDirectory().resolve(className + ".java");
            if (Files.exists(file)) {
                toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_ALREADY_EXISTS, className));
                scriptEditorView.open(file);
                return;
            }
            Files.writeString(file, String.format(SCRIPT_TEMPLATE, className, className));
            reloadScripts();
            attachScriptComponent(className, target);
            scriptEditorView.open(file);
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_CREATION_FAILED,
                    error.getMessage()));
        }
    }

    private void attachScriptComponent(String className, GameObject target) {
        Optional<ComponentRegistry.Entry> entry = findScriptEntry(className);
        if (entry.isEmpty()) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_DID_NOT_COMPILE, className));
            return;
        }
        if (target.getComponent(entry.get().componentClass()).isPresent()) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_COMPONENT_ALREADY_ON,
                    entry.get().displayName(), target.name()));
            return;
        }
        history().execute(new AddComponentCommand(target, entry.get().componentClass()));
        toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_ATTACHED_COMPONENT,
                entry.get().displayName(), target.name()));
    }

    private Optional<ComponentRegistry.Entry> findScriptEntry(String className) {
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (entry.componentClass().getName().equals(className)
                    || entry.componentClass().getSimpleName().equals(className)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private void attachScriptToSelected(Path scriptPath) {
        String className = scriptPath.getFileName().toString().replace(".java", "");
        Optional<GameObject> selected = workspace.active().selection().get();
        if (selected.isEmpty()) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SELECT_GAMEOBJECT_FIRST));
            return;
        }
        attachScriptComponent(className, selected.get());
    }

    private List<String> projectActionNames() {
        return projectStore.readInputActions(project).stream().map(InputAction::name).toList();
    }

    private void onShaderNodePreviewsToggled(boolean enabled) {
        preferences = preferences.withShaderNodePreviewsEnabled(enabled);
        persistPreferences();
    }

    private void onShaderGraphGenerated(Path generatedFile) {
        sceneHost.notifyShaderFileSaved(generatedFile);
        assetBrowserView.refreshAssets();
    }

    private void onScriptFileSaved(Path savedFile) {
        String name = savedFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (COMPILED_SCRIPT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            reloadScripts();
        }
        if (SHADER_FILE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            sceneHost.notifyShaderFileSaved(savedFile);
        }
    }

    private void reloadScripts() {
        scriptEditorView.clearDiagnostics();
        scriptService.reload();
        assetBrowserView.refreshAssets();
        graphEditorView.refreshReflectionNodes();
    }

    private void onScriptMessage(String message) {
        editorConsole.system(message);
        scriptEditorView.acceptCompilerMessage(message);
    }

    private void startPlay() {
        try {
            playController.start();
            playedDocument = workspace.active();
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_PLAY_FAILED, error.getMessage()));
        }
    }

    private void openSettings() {
        if (sceneHost.isInitialized()) {
            settingsDialog.attachRenderTuning(workspace.active().scene().postProcess(), sceneHost.skySettings(),
                    sceneHost.meshRenderSystem());
        }
        settingsDialog.attachPostEffects(settingsPostEffectsSection,
                () -> workspace.active().scene().postEffects(),
                () -> workspace.active().markDirty());
        settingsDialog.openFor(projectStore.readSettings(project), preferences, project,
                projectStore.readQuality(project), projectStore.readInputActions(project));
    }

    private void onSettingsSaved(EditorSettings settings) {
        try {
            projectStore.writeSettings(project, settings);
            projectStore.writeQuality(project, settingsDialog.buildQuality());
            projectStore.writeInputActions(project, settingsDialog.buildInputActions());
            workspace.active().markDirty();
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SETTINGS_SAVED));
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SETTINGS_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    private void onPreferencesSaved(EditorPreferences updated) {
        preferences = updated;
        applyPreferences();
        persistPreferences();
    }

    private void persistPreferences() {
        GpuLauncher.persist(preferences.gpuPreference());
        try {
            preferences.save(EditorPreferences.defaultFile());
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_PREFERENCES_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    private void toggleSnapPreference() {
        gizmoState.toggleSnap();
        preferences = preferences.withSnapEnabled(gizmoState.snapEnabled());
        persistPreferences();
    }

    private void toggleGridPreference() {
        viewportView.setShowGrid(!viewportView.showGrid());
        preferences = preferences.withGridVisible(viewportView.showGrid());
        persistPreferences();
    }

    private void onViewportTuningChanged(float overlayThickness, float gridFadeDistance) {
        viewportView.setOverlayThicknessMultiplier(overlayThickness);
        viewportView.setGridFadeDistance(gridFadeDistance);
    }

    private void onTextureFilterChanged(Path textureFile) {
        String absolute = textureFile.toAbsolutePath().toString();
        SamplerFilter filter = Texture2D.metaFilter(absolute);
        List<TextureHandle> loaded = sceneHost.engine().assets().loadedMatching(TextureHandle.class,
                path -> TexturePathPrefixes.stripPrefixes(path).equals(absolute));
        for (TextureHandle handle : loaded) {
            sceneHost.backend().updateTextureFilter(handle, filter);
        }
    }

    private void onAtlasSaved(Path atlasFile) {
        sceneHost.engine().assets().unload(atlasFile.toAbsolutePath().toString());
        toasts.show("Atlas saved: " + atlasFile.getFileName());
    }

    private void onMeshBaked(Path output) {
        toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_MESH_BAKED, output.getFileName()));
        assetBrowserView.refreshAssets();
    }

    @Override
    public void dispose() {
        shell.clearFileDropHandler();
        playSession.stop();
        playController.stop();
        viewportView.dispose();
        graphEditorView.shutdown();
        thumbnailCache.shutdown();
        imagePreview.dispose();
        spriteEditorWindow.dispose();
        tilePalettePanel.dispose();
        meshThumbnailer.shutdown();
    }
}
