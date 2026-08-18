package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.inspector.InspectorDependencies;
import fr.epistudio.epysia.editor.ui.kit.Toggles;
import fr.epistudio.epysia.editor.ui.kit.Toolbars;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.commands.CommandRegistry;
import fr.epistudio.epysia.editor.commands.EditorCommand;
import fr.epistudio.epysia.editor.assets.MeshThumbnailer;
import fr.epistudio.epysia.editor.assets.AssetReloadService;
import fr.epistudio.epysia.editor.assets.ProceduralTexturePreview;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.baking.ImpostorBaker;
import fr.epistudio.epysia.render.baking.OctahedralImpostorBaker;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.InstantiatePrefabCommand;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.ui.kit.DocumentTabs;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.importer.AssetImportPipeline;
import fr.epistudio.epysia.editor.importer.AssetImporterRegistry;
import fr.epistudio.epysia.editor.importer.GltfAssetImporter;
import fr.epistudio.epysia.editor.log.EditorConsole;
import fr.epistudio.epysia.editor.notify.ToastCenter;
import fr.epistudio.epysia.editor.play.EmbeddedPlaySession;
import fr.epistudio.epysia.editor.play.NetworkPlaySettings;
import fr.epistudio.epysia.editor.play.PlayController;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.preview.ShaderGraphPreviewService;
import fr.epistudio.epysia.editor.preview.VfxPreviewPanel;
import fr.epistudio.epysia.gpu.GpuLauncher;
import fr.epistudio.epysia.editor.runtime.EditorCamera;
import fr.epistudio.epysia.editor.runtime.ToolScriptTicker;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.scene.SceneWorkspace;
import fr.epistudio.epysia.editor.scene.StarterSceneContent;
import fr.epistudio.epysia.editor.scripts.IdeLauncher;
import fr.epistudio.epysia.editor.scripts.IdeProjectWriter;
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
import fr.epistudio.epysia.prefab.PrefabRefresher;
import fr.epistudio.epysia.prefab.PrefabWriter;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.editor.gizmo.PhysicsDebugOverlay;
import fr.epistudio.epysia.scripting.EditorTickable;
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
import fr.epistudio.epysia.project.NetworkSettings;
import fr.epistudio.epysia.project.RenderSettings;
import fr.epistudio.epysia.project.SteamSettings;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import java.util.List;

public final class EditorView implements FrameView {


    private static final float TOOLBAR_TOOLS_HEIGHT = 30.0f;
    private static final float MINIMUM_TAB_REGION_WIDTH = 120.0f;
    private static final float TOOLBAR_EDGE_PADDING = 6.0f;
    private static final int PLAY_BUTTON_COUNT = 4;
    private static final float STATUS_BAR_HEIGHT = 26.0f;
    private static final float SPAWN_DISTANCE = 4.0f;
    private static final float TWO_DIMENSIONAL_FRAME_MINIMUM_RADIUS = 3.0f;
    private static final String PREFABS_DIRECTORY_NAME = "prefabs";
    private static final String PREFAB_EXTENSION = ".epyprefab";
    private static final String ABOUT_POPUP_ID = "about-epysia";
    private static final String CLOSE_SCENE_POPUP_ID = "close-scene-unsaved-changes";
    private static final ScriptLanguages SCRIPT_LANGUAGES = ScriptLanguages.discover();
    private static final Set<String> SHADER_FILE_EXTENSIONS = Set.of(".glsl", ".vert", ".frag");
    private static final int HOST_WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNavFocus
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings;


    private final Project project;
    private final ComponentRegistry componentRegistry;
    private final ProjectStore projectStore;
    private final EditorScene3DHost sceneHost;
    private final EditorCamera editorCamera;
    private static final float UI_SCALE_STEP = 0.1f;
    private static final float BRAND_MARGIN = 6.0f;

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
    private final GameObjectCreationMenu creationMenu;
    private final AssetImportPipeline importPipeline;
    private final ScriptService scriptService;
    private final GizmoState gizmoState = new GizmoState();
    private final DockLayout dockLayout = new DockLayout();
    private final CommandRegistry commands = new CommandRegistry();
    private final CommandPaletteView commandPalette = new CommandPaletteView();
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
    private final LibrariesSection librariesSection;
    private final MeshBakeDialog meshBakeDialog;
    private final ExportGameDialog exportGameDialog;
    private final ToolScriptTicker toolScripts = new ToolScriptTicker();
    private final AssetReloadService assetReloads;
    private final ProceduralTexturePreview proceduralPreview;
    private final NameDialog nameDialog = new NameDialog("##editor-name-dialog");
    private final NewScriptDialog newScriptDialog =
            new NewScriptDialog(SCRIPT_LANGUAGES, this::createNewScript);
    private Optional<GameObject> scriptTarget = Optional.empty();
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
        sceneHost.engine().assets().attachProject(project.rootDirectory());
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
        this.objectFactory = new GameObjectFactory(active, sceneHost.engine(), editorCamera::twoDimensional);
        this.creationMenu = new GameObjectCreationMenu(objectFactory);
        this.importPipeline = new AssetImportPipeline(buildImporterRegistry(componentRegistry, sceneHost.backend()));
        this.scriptEditorView = new ScriptEditorView(componentRegistry, icons, toasts, this::onScriptFileSaved);
        this.shaderGraphPreviews = new ShaderGraphPreviewService(sceneHost.window(), sceneHost.backend());
        this.vfxPreviewPanel = new VfxPreviewPanel(sceneHost.window(), sceneHost.backend());
        this.graphEditorView = new GraphEditorView(componentRegistry, toasts, active,
                thumbnailCache, this::onShaderGraphGenerated, shaderGraphPreviews, vfxPreviewPanel,
                new AssetPicker(project), () -> preferences.shaderNodePreviewsEnabled(),
                this::onShaderNodePreviewsToggled, this::projectActionNames);
        this.proceduralPreview = new ProceduralTexturePreview(sceneHost.backend());
        this.assetReloads = new AssetReloadService(project.rootDirectory(), sceneHost::engine,
                workspace::documents);
        this.scriptService = new ScriptService(project, componentRegistry, serializer, workspace,
                this::onScriptMessage, sceneHost::applyProjectRenderSetups);
        this.tilePalettePanel = new TilePalettePanel(sceneHost.backend(), sceneHost.engine(), active, tileBrush);
        this.viewportView = new ViewportView(sceneHost, editorCamera, active, gizmoState,
                shell.windowHandle(), playSession, icons, objectFactory, importPipeline, tilePalettePanel);
        this.hierarchyView = new HierarchyView(active, componentRegistry, toasts, icons, this::saveAsPrefab,
                viewportView::frameObject, objectFactory, this::spawnPositionInFront);
        this.spriteEditorWindow = new SpriteEditorWindow(
                new ImagePreviewTexture(sceneHost.backend()), project.locator(), this::onAtlasSaved);
        this.tilemapDockView = new TilemapDockView(active, sceneHost.engine(), icons, tilePalettePanel,
                viewportView::enablePainting, editorCamera::twoDimensional);
        this.inspectorView = new InspectorView(
                new InspectorDependencies(active, componentRegistry, toasts, icons, thumbnailCache,
                        project, objectFactory, sceneHost.engine()),
                new AssetPicker(project), this::promptNewScriptFor,
                graphEditorView::open, this::selectedBrowserAssetPath,
                new AtlasInspectorSection(spriteEditorWindow::open),
                new TextureInspectorSection(imagePreview, this::onTextureFilterChanged),
                new ProceduralTextureSection(proceduralPreview, toasts, this::onProceduralTextureSaved),
                tilemapDockView::focus);
        this.consoleView = new ConsoleView(playController, editorConsole, project.scriptsDirectory(),
                location -> scriptEditorView.open(location.file(), location.line()));
        this.meshBakeDialog = new MeshBakeDialog(toasts, this::onMeshBaked);
        this.assetBrowserView = new AssetBrowserView(project, toasts, icons, thumbnailCache, meshThumbnailer,
                scriptEditorView::open, meshBakeDialog::openFor,
                this::instantiatePrefabAtOrigin, this::openScenePath, this::attachScriptToSelected,
                graphEditorView::open, spriteEditorWindow::open, importPipeline);
        this.settingsDialog = new SettingsDialog(this::onSettingsSaved, this::onPreferencesSaved,
                this::onNetworkSaved, this::onSteamSaved, this::onRenderSaved,
                this::onViewportTuningChanged, icons);
        this.settingsPostEffectsSection = new PostEffectsSection(project, thumbnailCache);
        this.librariesSection = new LibrariesSection(toasts, this::reloadScripts);
        this.profilerView = new ProfilerView(sceneHost, shell, active, viewportView, panelTimings);
        this.lightingView = new LightingView(sceneHost, active, project.rootDirectory());
        this.exportGameDialog = new ExportGameDialog(project, toasts);
        shell.setFileDropHandler(assetBrowserView::importExternalFiles);
        finishSetup();
    }

    private Optional<Path> selectedBrowserAssetPath() {
        return assetBrowserView == null ? Optional.empty() : assetBrowserView.selectedEntryPath();
    }

    private static AssetImporterRegistry buildImporterRegistry(ComponentRegistry componentRegistry,
                                                               RenderBackend backend) {
        AssetImporterRegistry registry = new AssetImporterRegistry();
        ImpostorBaker impostorBaker = new OctahedralImpostorBaker(backend, ShaderLoader.autoDetect());
        registry.register(new GltfAssetImporter(componentRegistry, Optional.of(impostorBaker)));
        return registry;
    }

    private void registerCommands() {
        registerFileCommands();
        registerEditCommands();
        registerWindowCommands();
        commands.add(EditorCommand.of("play.toggle",
                () -> I18n.translate(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW),
                () -> I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PLAY),
                this::togglePlaySession).withShortcut("Ctrl+P"));
    }

    private void registerFileCommands() {
        Supplier<String> group = () -> I18n.translate(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE);
        commands.add(command("file.new-scene", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_SCENE, workspace::create));
        commands.add(command("file.open-scene", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_OPEN_SCENE, this::openSceneDialog));
        commands.add(command("file.save", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_SAVE, this::saveScene).withShortcut("Ctrl+S"));
        commands.add(command("file.save-as", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_SAVE_AS, this::saveSceneAs));
        commands.add(command("file.export-game", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_EXPORT_GAME,
                () -> exportGameDialog.open(workspace.active().name())));
        commands.add(command("file.reload-scripts", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_RELOAD_SCRIPTS, this::reloadScripts));
        commands.add(command("file.open-scripts-in-ide", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_OPEN_SCRIPTS_IN_IDE, this::openScriptsInIde));
        commands.add(command("file.open-project", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_OPEN_PROJECT, onOpenProjectSelector));
        commands.add(command("file.exit", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_EXIT, shell::requestClose));
    }

    private void registerEditCommands() {
        Supplier<String> group = () -> I18n.translate(TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT);
        commands.add(command("edit.undo", group, TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_UNDO,
                () -> history().undo()).withShortcut("Ctrl+Z").availableWhen(() -> history().canUndo()));
        commands.add(command("edit.redo", group, TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_REDO,
                () -> history().redo()).withShortcut("Ctrl+Y").availableWhen(() -> history().canRedo()));
        commands.add(command("edit.duplicate", group, TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_DUPLICATE,
                hierarchyView::duplicateSelected).withShortcut("Ctrl+D"));
        commands.add(command("edit.delete", group, TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_DELETE,
                hierarchyView::askDeleteSelected).withShortcut("Del"));
        commands.add(command("edit.settings", group, TextKey.EDITOR_EDITOR_VIEW_MENU_EDIT_SETTINGS,
                this::openSettings));
    }

    private void registerWindowCommands() {
        Supplier<String> group = () -> I18n.translate(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW);
        commands.add(command("window.reset-layout", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_RESET_LAYOUT, dockLayout::requestDefaultLayout));
        commands.add(command("window.grid", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_GRID, this::toggleGridPreference));
        commands.add(command("window.collider-wireframes", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_COLLIDER_WIREFRAMES,
                () -> viewportView.setShowColliderWireframes(!viewportView.showColliderWireframes())));
        commands.add(command("window.navmesh-debug", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_NAVMESH_DEBUG,
                () -> viewportView.setShowNavMesh(!viewportView.showNavMesh())));
        commands.add(command("window.profiler", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_PROFILER,
                () -> profilerView.setVisible(!profilerView.isVisible())));
        commands.add(command("window.lighting", group,
                TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_LIGHTING,
                () -> lightingView.setVisible(!lightingView.isVisible())));
    }

    private static EditorCommand command(String id, Supplier<String> group, TextKey label,
                                         Runnable action) {
        return EditorCommand.of(id, group, () -> I18n.translate(label), action);
    }

    private void finishSetup() {
        registerCommands();
        applyPreferences();
        scriptService.reload();
        refreshScriptSymbols();
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
        viewportView.setSceneTabsRenderer(this::renderSceneTabsStrip);
        viewportView.setMainToolbarRenderer(this::renderViewportToolbar);
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
        advanceEditModeAnimation(deltaSeconds);
        tickEditorComponents(deltaSeconds);
        pollBackgroundState(deltaSeconds);
        pollAssetChanges(deltaSeconds);
        renderMainMenuBar();
        renderHostWindow();
        renderPanels(deltaSeconds);
        renderDialogs();
        commandPalette.render(commands);
        handleGlobalShortcuts();
        playSession.frame(deltaSeconds);
    }

    private void advanceEditModeAnimation(float deltaSeconds) {
        if (playSession.isActive() || playController.isRunning()) {
            return;
        }
        sceneHost.advanceAnimation(deltaSeconds);
    }

    private void pollAssetChanges(float deltaSeconds) {
        if (playSession.isActive() || playController.isRunning()) {
            return;
        }
        List<Path> reloaded = assetReloads.poll(deltaSeconds);
        if (reloaded.isEmpty()) {
            return;
        }
        sceneHost.requestViewportRedraw();
        toasts.show(reloaded.size() == 1
                ? I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_ASSET_RELOADED,
                        reloaded.get(0).getFileName())
                : I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_ASSETS_RELOADED, reloaded.size()));
    }

    private void tickEditorComponents(float deltaSeconds) {
        if (playSession.isActive() || playController.isRunning()) {
            toolScripts.reset();
            return;
        }
        Scene scene = workspace.active().scene();
        List<EditorTickable> tickables = List.copyOf(scene.componentsOf(EditorTickable.class));
        sceneHost.engine().backgroundTasks().deliverCompleted();
        for (EditorTickable tickable : tickables) {
            tickEditorComponent(tickable, deltaSeconds);
        }
        boolean toolsRan = toolScripts.tick(scene, sceneHost.engine(), deltaSeconds, toasts::show);
        if (tickables.isEmpty() && !toolsRan) {
            return;
        }
        scene.advanceTick();
        sceneHost.requestViewportRedraw();
    }

    private void tickEditorComponent(EditorTickable tickable, float deltaSeconds) {
        try {
            tickable.onEditorUpdate(sceneHost.engine(), deltaSeconds);
        } catch (RuntimeException error) {
            toasts.show(tickable.getClass().getSimpleName() + " editor update failed: " + error.getMessage());
        }
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
        dockLayout.buildIfRequested(viewport);
        ImGui.dockSpace(dockLayout.dockspaceId(), 0.0f, -EditorScale.of(STATUS_BAR_HEIGHT));
        renderStatusBar();
        ImGui.end();
    }

    private void renderPanels(float deltaSeconds) {
        boolean playing = playSession.isActive();
        panelTimings.beginFrame();
        ImGui.beginDisabled(playing);
        panelTimings.measure("Hierarchy", hierarchyView::render);
        ImGui.endDisabled();
        inspectorView.setPlayModeActive(playing);
        panelTimings.measure("Inspector", inspectorView::render);
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
        newScriptDialog.render();
        renderAboutPopup();
        renderCloseScenePopup();
    }

    private void renderMainMenuBar() {
        if (!ImGui.beginMainMenuBar()) {
            return;
        }
        renderBrandMark();
        renderFileMenu();
        renderEditMenu();
        renderGameObjectMenu();
        renderWindowMenu();
        renderHelpMenu();
        Toolbars.pushFlatButtons();
        renderPlayControls();
        renderRunGameButton();
        Toolbars.popFlatButtons();
        ImGui.endMainMenuBar();
    }

    private void renderBrandMark() {
        float size = ImGui.getFontSize();
        float lineY = ImGui.getCursorPosY();
        ImGui.dummy(EditorScale.of(BRAND_MARGIN), 0.0f);
        ImGui.sameLine();
        ImGui.setCursorPosY(lineY + (ImGui.getFrameHeight() - size) * 0.5f);
        icons.draw(EditorIcon.EPYSIA_LOGO, size);
        ImGui.sameLine();
        ImGui.setCursorPosY(lineY);
        ImGui.dummy(EditorScale.of(BRAND_MARGIN), 0.0f);
        ImGui.sameLine();
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
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_NEW_SCRIPT,
                "menu-file-new-script"))) {
            promptNewScript();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_RELOAD_SCRIPTS,
                "menu-file-reload-scripts"))) {
            reloadScripts();
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_FILE_OPEN_SCRIPTS_IN_IDE,
                "menu-file-open-scripts-in-ide"))) {
            openScriptsInIde();
        }
    }

    private void openScriptsInIde() {
        Optional<String> failure = IdeProjectWriter.write(project);
        if (failure.isPresent()) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_IDE_PROJECT_FAILED, failure.get()));
            return;
        }
        IdeLauncher.open(project.rootDirectory()).ifPresentOrElse(
                error -> toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_IDE_PROJECT_FAILED, error)),
                () -> toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_IDE_PROJECT_WRITTEN,
                        project.rootDirectory())));
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
        creationMenu.renderItems(spawnPositionInFront());
        ImGui.endDisabled();
        ImGui.endMenu();
    }

    private void renderPhysicsDebugMenu() {
        if (!ImGui.beginMenu(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_WINDOW_PHYSICS_DEBUG,
                "menu-window-physics-debug"))) {
            return;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_PHYSICS_DEBUG_ENABLED,
                "menu-physics-debug-enabled"), "", viewportView.showPhysicsDebug())) {
            viewportView.setShowPhysicsDebug(!viewportView.showPhysicsDebug());
        }
        PhysicsDebugOverlay.Options options = viewportView.physicsDebugOptions();
        ImGui.separator();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_PHYSICS_DEBUG_CENTER_OF_MASS,
                "menu-physics-debug-center"), "", options.centerOfMass)) {
            options.centerOfMass = !options.centerOfMass;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_PHYSICS_DEBUG_VELOCITIES,
                "menu-physics-debug-velocities"), "", options.velocities)) {
            options.velocities = !options.velocities;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_PHYSICS_DEBUG_SLEEP,
                "menu-physics-debug-sleep"), "", options.sleepState)) {
            options.sleepState = !options.sleepState;
        }
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_PHYSICS_DEBUG_JOINTS,
                "menu-physics-debug-joints"), "", options.joints)) {
            options.joints = !options.joints;
        }
        ImGui.endMenu();
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
        renderPhysicsDebugMenu();
        if (ImGui.menuItem(I18n.label(TextKey.EDITOR_EDITOR_VIEW_MENU_NAVMESH_DEBUG,
                "menu-navmesh-debug"), "", viewportView.showNavMesh())) {
            viewportView.setShowNavMesh(!viewportView.showNavMesh());
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
        Texts.muted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_ABOUT_PROJECT,
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

    private void renderSceneTabsStrip() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, EditorStyle.itemSpacingX(), 0.0f);
        ImGui.beginChild("##scene-tabs-strip", 0.0f, Toolbars.buttonHeight(), false);
        renderSceneTabs();
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private void renderViewportToolbar() {
        Toolbars.pushFlatButtons();
        renderGizmoToolButtons();
        Toolbars.groupSeparator();
        renderToggleButtons();
        Toolbars.popFlatButtons();
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
        renderToolButton("tool-pivot", EditorIcon.TOOL_PIVOT, GizmoState.Tool.PIVOT,
                TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PIVOT);
        Toolbars.groupSeparator();
        TextKey spaceKey = gizmoState.worldSpace()
                ? TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_WORLD
                : TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_LOCAL;
        if (Toolbars.textButton(I18n.label(spaceKey, "toolbar-gizmo-space"))) {
            gizmoState.toggleSpace();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_TOGGLE_GIZMO_SPACE);
    }

    private void renderTwoDimensionalToggle() {
        boolean active = editorCamera.twoDimensional();
        if (Toggles.text("toolbar-2d", I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_TWO_DIMENSIONAL), active)) {
            toggleTwoDimensionalPreference();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_TWO_DIMENSIONAL_TOOLTIP);
    }

    private void toggleTwoDimensionalPreference() {
        editorCamera.setTwoDimensional(!editorCamera.twoDimensional());
        preferences = preferences.withViewport2DMode(editorCamera.twoDimensional());
        persistPreferences();
    }

    private void renderToolButton(String id, EditorIcon icon, GizmoState.Tool tool, TextKey tooltipKey) {
        if (icons.toggleButton(id, icon, EditorStyle.iconSizeToolbar(), gizmoState.tool() == tool)) {
            gizmoState.setTool(tool);
        }
        tooltip(tooltipKey);
    }

    private void renderToggleButtons() {
        renderTwoDimensionalToggle();
        Toolbars.nextItem();
        if (icons.toggleButton("toolbar-snap", EditorIcon.SNAP, EditorStyle.iconSizeToolbar(),
                gizmoState.snapEnabled())) {
            toggleSnapPreference();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SNAP);
        Toolbars.nextItem();
        if (icons.toggleButton("toolbar-grid", EditorIcon.GRID, EditorStyle.iconSizeToolbar(),
                viewportView.showGrid())) {
            toggleGridPreference();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_GRID);
        Toolbars.groupSeparator();
        renderSaveButton();
    }

    private void renderSaveButton() {
        if (icons.iconButton("toolbar-save", EditorIcon.SAVE, EditorStyle.iconSizeToolbar())) {
            saveScene();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_SAVE_SCENE);
    }

    private void renderPlayControls() {
        Toolbars.groupSeparator();
        float leftEdge = ImGui.getCursorPosX();
        float centered = (ImGui.getWindowWidth() - playGroupWidth()) * 0.5f;
        ImGui.sameLine(Math.max(centered, leftEdge));
        renderEmbeddedPlayButtons();
    }

    private static float playGroupWidth() {
        return iconButtonWidth() * PLAY_BUTTON_COUNT
                + ImGui.getStyle().getItemSpacingX() * (PLAY_BUTTON_COUNT - 1);
    }

    private static float iconButtonWidth() {
        return EditorStyle.iconSizeToolbar() + ImGui.getStyle().getFramePaddingX() * 2.0f;
    }

    private static float labelButtonWidth(String label) {
        return ImGui.calcTextSize(label).x + ImGui.getStyle().getFramePaddingX() * 2.0f;
    }

    private void renderEmbeddedPlayButtons() {
        boolean active = playSession.isActive();
        ImGui.beginDisabled(active);
        if (icons.iconButton("toolbar-play", EditorIcon.PLAY, EditorStyle.iconSizeToolbar())) {
            playSession.start();
        }
        ImGui.endDisabled();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PLAY);
        ImGui.sameLine();
        renderPauseStepStopButtons(active);
    }

    private void renderPauseStepStopButtons(boolean active) {
        ImGui.beginDisabled(!active);
        if (icons.toggleButton("toolbar-pause", EditorIcon.PAUSE, EditorStyle.iconSizeToolbar(),
                playSession.state() == EmbeddedPlaySession.State.PAUSED)) {
            playSession.togglePause();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_PAUSE);
        ImGui.sameLine();
        ImGui.beginDisabled(playSession.state() != EmbeddedPlaySession.State.PAUSED);
        if (icons.iconButton("toolbar-step", EditorIcon.REDO, EditorStyle.iconSizeToolbar())) {
            playSession.step();
        }
        ImGui.endDisabled();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_STEP);
        ImGui.sameLine();
        if (icons.iconButton("toolbar-stop", EditorIcon.STOP, EditorStyle.iconSizeToolbar())) {
            playSession.stop();
        }
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_STOP);
        ImGui.endDisabled();
    }

    private void renderRunGameButton() {
        boolean subprocessRunning = playController.isRunning();
        String runLabel = I18n.label(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_RUN_GAME, "toolbar-run-game");
        String killLabel = I18n.label(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_KILL, "toolbar-kill-game");
        ImGui.sameLine(rightAlignedX(runGroupWidth(runLabel, killLabel, subprocessRunning)));
        ImGui.beginDisabled(subprocessRunning || playSession.isActive());
        if (Toolbars.textButton(DocumentTabs.reserveIconSpace(runLabel))) {
            startPlay();
        }
        DocumentTabs.decorate(icons.textureId(EditorIcon.PLAY));
        ImGui.endDisabled();
        renderNetworkPlayPopup();
        tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_RUN_GAME_TOOLTIP);
        if (subprocessRunning) {
            ImGui.sameLine();
            if (ImGui.button(killLabel)) {
                playController.stop();
            }
            tooltip(TextKey.EDITOR_EDITOR_VIEW_TOOLBAR_KILL_TOOLTIP);
        }
    }

    private void renderNetworkPlayPopup() {
        if (!ImGui.beginPopupContextItem("network-play-settings")) {
            return;
        }
        NetworkPlaySettings settings = playController.networkSettings();
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_NETWORK_PLAY_TITLE));
        ImGui.separator();
        renderEditorRoleChoices(settings);
        ImGui.separator();
        renderNetworkPlayNumbers(settings);
        ImGui.endPopup();
    }

    private static void renderEditorRoleChoices(NetworkPlaySettings settings) {
        for (NetworkPlaySettings.EditorRole role : NetworkPlaySettings.EditorRole.values()) {
            if (ImGui.radioButton(role.name(), settings.editorRole() == role)) {
                settings.setEditorRole(role);
            }
        }
    }

    private static void renderNetworkPlayNumbers(NetworkPlaySettings settings) {
        int[] extraClients = {settings.extraClients()};
        if (ImGui.sliderInt(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_NETWORK_PLAY_EXTRA_CLIENTS),
                extraClients, 0, 7)) {
            settings.setExtraClients(extraClients[0]);
        }
        int[] port = {settings.port()};
        if (ImGui.dragInt(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_NETWORK_PLAY_PORT), port, 1.0f, 1, 65_535)) {
            settings.setPort(port[0]);
        }
    }

    private static float runGroupWidth(String runLabel, String killLabel, boolean subprocessRunning) {
        float width = labelButtonWidth(runLabel);
        if (subprocessRunning) {
            width += ImGui.getStyle().getItemSpacingX() + labelButtonWidth(killLabel);
        }
        return width;
    }

    private static float rightAlignedX(float groupWidth) {
        return ImGui.getWindowWidth() - groupWidth - EditorScale.of(TOOLBAR_EDGE_PADDING);
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
            Texts.muted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SCENE_PLAYING,
                    workspace.active().name()));
            return;
        }
        if (!ImGui.beginTabBar("##scene-tabs", ImGuiTabBarFlags.AutoSelectNewTabs | ImGuiTabBarFlags.DrawSelectedOverline)) {
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
        String label = DocumentTabs.reserveIconSpace(document.name()) + "##" + document.filePath();
        boolean selected = ImGui.beginTabItem(label, keepOpen, flags);
        DocumentTabs.decorate(icons.textureId(EditorIcon.PACKED_SCENE));
        boolean middleClicked = DocumentTabs.closeRequestedByMiddleClick();
        if (selected) {
            activateDocument(document);
            ImGui.endTabItem();
        }
        if (!keepOpen.get() || middleClicked) {
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
        ImGui.beginChild("##status-bar", 0.0f, EditorScale.of(STATUS_BAR_HEIGHT), false);
        ImGui.setCursorPosX(EditorStyle.windowPadding());
        renderPlayState();
        ImGui.sameLine();
        Texts.muted(objectCountLabel());
        renderLastLogLine();
        renderFramerate();
        ImGui.endChild();
    }

    private void renderPlayState() {
        if (playSession.state() == EmbeddedPlaySession.State.PLAYING) {
            Texts.colored(EditorStyle.COLOR_ACCENT, I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_PLAYING));
        } else if (playSession.state() == EmbeddedPlaySession.State.PAUSED) {
            Texts.colored(EditorStyle.COLOR_WARNING, I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_PAUSED));
        } else if (playController.isRunning()) {
            Texts.colored(EditorStyle.COLOR_ACCENT,
                    I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_GAME_RUNNING));
        } else {
            Texts.muted(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_READY));
        }
        ImGui.sameLine();
        Texts.muted(contextHint());
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
            Texts.muted(truncate(lastLine.get()));
        }
    }

    private void renderFramerate() {
        String fps = I18n.translate(TextKey.EDITOR_EDITOR_VIEW_STATUS_FPS,
                String.format(Locale.ROOT, "%.0f", ImGui.getIO().getFramerate()));
        ImGui.sameLine(ImGui.getWindowWidth() - ImGui.calcTextSize(fps).x - EditorStyle.windowPadding());
        Texts.colored(EditorStyle.COLOR_ACCENT, fps);
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
        if (commandPalette.isVisible()) {
            return;
        }
        if (ImGui.getIO().getKeyCtrl() && ImGui.getIO().getKeyShift()
                && ImGui.isKeyPressed(ImGuiKey.P)) {
            commandPalette.open();
            return;
        }
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
        if (ImGui.isKeyPressed(ImGuiKey.P) && !ImGui.getIO().getKeyShift()) {
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
        applyZoomShortcuts();
    }

    private void applyZoomShortcuts() {
        if (ImGui.isKeyPressed(ImGuiKey.Equal) || ImGui.isKeyPressed(ImGuiKey.KeypadAdd)) {
            zoomBy(UI_SCALE_STEP);
        }
        if (ImGui.isKeyPressed(ImGuiKey.Minus) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract)) {
            zoomBy(-UI_SCALE_STEP);
        }
        if (ImGui.isKeyPressed(ImGuiKey._0) || ImGui.isKeyPressed(ImGuiKey.Keypad0)) {
            applyUiScale(EditorScale.detectDisplayScale());
        }
    }

    private void zoomBy(float delta) {
        applyUiScale(EditorScale.factor() + delta);
    }

    private void applyUiScale(float requested) {
        float clamped = Math.clamp(requested, EditorScale.MINIMUM_FACTOR, EditorScale.MAXIMUM_FACTOR);
        EditorScale.setFactor(clamped);
        EditorStyle.apply();
        preferences = preferences.withUiScale(clamped);
        persistPreferences();
        toasts.show(String.format(java.util.Locale.ROOT, "Interface scale %.0f%%", clamped * 100.0f));
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
        if (ImGui.isKeyPressed(ImGuiKey.P, false)) {
            gizmoState.setTool(GizmoState.Tool.PIVOT);
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
            refreshPrefabInstances();
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_PREFAB_SAVE_FAILED,
                    error.getMessage()));
        }
    }

    private void refreshPrefabInstances() {
        PrefabRefresher refresher = new PrefabRefresher(this::readPrefabText,
                new SceneSerializer(componentRegistry)::applyFields);
        for (SceneDocument document : workspace.documents()) {
            refresher.refresh(document.scene());
        }
    }

    private Optional<String> readPrefabText(String prefabSource) {
        return project.locator().file(prefabSource).flatMap(EditorView::readFileQuietly);
    }

    private static Optional<String> readFileQuietly(Path path) {
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private void instantiatePrefabAtOrigin(Path prefabPath) {
        history().execute(workspace.active().selection().get()
                .map(parent -> new InstantiatePrefabCommand(prefabPath, parent))
                .orElseGet(() -> new InstantiatePrefabCommand(prefabPath, new Vector3f())));
    }

    private void promptNewScript() {
        scriptTarget = Optional.empty();
        newScriptDialog.open();
    }

    private void promptNewScriptFor(GameObject target) {
        scriptTarget = Optional.of(target);
        newScriptDialog.open();
    }

    private void createNewScript(ScriptLanguage language, String className) {
        try {
            Files.createDirectories(project.scriptsDirectory());
            Path target = project.scriptsDirectory().resolve(className + language.sourceExtension());
            if (Files.exists(target)) {
                toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_ALREADY_EXISTS, className));
                scriptEditorView.open(target);
                return;
            }
            Files.writeString(target, language.behaviourTemplate(className));
            reloadScripts();
            scriptTarget.ifPresent(owner -> attachScriptComponent(className, owner));
            scriptEditorView.open(target);
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_TOAST_SCRIPT_CREATION_FAILED,
                    error.getMessage()));
        }
    }

    static boolean isScriptClassName(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        return name.chars().allMatch(Character::isJavaIdentifierPart);
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
        String className = SCRIPT_LANGUAGES.baseNameOf(scriptPath);
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
        if (SCRIPT_LANGUAGES.sourceExtensions().stream().anyMatch(name::endsWith)) {
            reloadScripts();
        }
        if (SHADER_FILE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            sceneHost.notifyShaderFileSaved(savedFile);
        }
    }

    private void reloadScripts() {
        scriptEditorView.clearDiagnostics();
        scriptService.reload();
        refreshScriptSymbols();
        assetBrowserView.refreshAssets();
        graphEditorView.refreshReflectionNodes();
    }

    private void refreshScriptSymbols() {
        scriptEditorView.refreshSymbols(project.libraries(), project.compiledScriptsDirectory());
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
        settingsDialog.attachLibraries(librariesSection);
        settingsDialog.openFor(projectStore.readSettings(project), preferences, project,
                projectStore.readQuality(project), projectStore.readInputActions(project),
                projectStore.readNetwork(project), projectStore.readSteam(project),
                projectStore.readRender(project));
    }

    private void onRenderSaved(RenderSettings settings) {
        try {
            projectStore.writeRender(project, settings);
        } catch (IOException failure) {
            toasts.show(failure.getMessage());
        }
    }

    private void onSteamSaved(SteamSettings settings) {
        try {
            projectStore.writeSteam(project, settings);
        } catch (IOException failure) {
            toasts.show(failure.getMessage());
        }
    }

    private void onNetworkSaved(NetworkSettings settings) {
        try {
            projectStore.writeNetwork(project, settings);
        } catch (IOException failure) {
            toasts.show(failure.getMessage());
        }
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

    private void onProceduralTextureSaved(Path file) {
        sceneHost.engine().assets().invalidate(project.locator().fromFile(file));
        sceneHost.requestViewportRedraw();
    }

    private void onTextureFilterChanged(Path textureFile) {
        AssetUri uri = project.locator().fromFile(textureFile);
        SamplerFilter filter = Texture2D.importSettings(textureFile.toAbsolutePath().toString()).filter();
        for (TextureHandle handle : sceneHost.engine().assets().loaded(TextureHandle.class, uri)) {
            sceneHost.backend().updateTextureFilter(handle, filter);
        }
    }

    private void onAtlasSaved(Path atlasFile) {
        sceneHost.engine().assets().invalidate(project.locator().fromFile(atlasFile));
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
        settingsDialog.dispose();
        meshThumbnailer.shutdown();
    }
}
