package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.input.UiInput;
import com.miry.ui.widgets.Button;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.MenuBar;
import com.miry.ui.widgets.ToastManager;
import fr.epistudio.epysia.editor.EditorPrimitiveRegistry;
import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.editor.command.builtin.AddGameObjectCommand;
import fr.epistudio.epysia.editor.command.builtin.RemoveGameObjectCommand;
import fr.epistudio.epysia.editor.project.Project;
import fr.epistudio.epysia.editor.serialization.SceneSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TopBarPanel extends Panel {

    private static final String TITLE = "TopBar";
    private static final int MENUBAR_HEIGHT = 30;
    private static final int PLAY_BUTTON_SIZE = 28;
    private static final int PLAY_BUTTON_GAP = 6;
    private static final int RIGHT_SECTION_PADDING = 14;

    private final EditorWorld world;
    private final EditorSceneHost sceneHost;
    private final Project project;
    private final ToastManager toasts;
    private final Runnable onQuit;
    private final Runnable onToggleConsole;
    private final Runnable onToggleFileSystem;
    private final MenuBar menuBar = new MenuBar();
    private final ContextMenu fileMenu = new ContextMenu();
    private final ContextMenu editMenu = new ContextMenu();
    private final ContextMenu projectMenu = new ContextMenu();
    private final ContextMenu viewMenu = new ContextMenu();
    private final ContextMenu helpMenu = new ContextMenu();
    private final Button playButton = new Button(Icon.PLAY);
    private final Button pauseButton = new Button(Icon.PAUSE);
    private final Button stopButton = new Button(Icon.STOP);
    private final SceneSerializer serializer;
    private final Path scenePath;

    public TopBarPanel(EditorWorld world, EditorSceneHost sceneHost, Project project, ToastManager toasts,
                       Runnable onQuit, Runnable onToggleConsole, Runnable onToggleFileSystem) {
        super(TITLE);
        this.world = world;
        this.sceneHost = sceneHost;
        this.project = project;
        this.toasts = toasts;
        this.onQuit = onQuit;
        this.onToggleConsole = onToggleConsole;
        this.onToggleFileSystem = onToggleFileSystem;
        this.serializer = new SceneSerializer(sceneHost.components());
        this.scenePath = project.defaultScenePath();
        buildMenus();
    }

    private void buildMenus() {
        buildFileMenu();
        buildEditMenu();
        buildProjectMenu();
        buildViewMenu();
        buildHelpMenu();
        menuBar.addMenu("File", fileMenu);
        menuBar.addMenu("Edit", editMenu);
        menuBar.addMenu("Project", projectMenu);
        menuBar.addMenu("View", viewMenu);
        menuBar.addMenu("Help", helpMenu);
    }

    private void buildFileMenu() {
        fileMenu.clear();
        fileMenu.addItem("New Scene", this::performNewScene);
        fileMenu.addItem("Open Scene", this::performLoad);
        fileMenu.addItem("Save Scene", this::performSave);
        fileMenu.addSeparator();
        fileMenu.addItem("New Script", this::performNewScript);
        fileMenu.addSeparator();
        fileMenu.addItem("Quit", onQuit);
    }

    private void buildEditMenu() {
        editMenu.clear();
        editMenu.addItem("Undo", this::performUndo);
        editMenu.addItem("Redo", this::performRedo);
        editMenu.addSeparator();
        editMenu.addItem("Duplicate Selected", this::duplicateSelected);
        editMenu.addItem("Delete Selected", this::deleteSelected);
    }

    private void performUndo() {
        if (!world.history().canUndo()) {
            toasts.show("Nothing to undo", 1.5f);
            return;
        }
        String label = world.history().peekUndoLabel();
        world.history().undo();
        toasts.show("Undo: " + label, 1.5f);
    }

    private void performRedo() {
        if (!world.history().canRedo()) {
            toasts.show("Nothing to redo", 1.5f);
            return;
        }
        String label = world.history().peekRedoLabel();
        world.history().redo();
        toasts.show("Redo: " + label, 1.5f);
    }

    private void buildProjectMenu() {
        projectMenu.clear();
        for (EditorPrimitiveRegistry.Entry entry : sceneHost.primitives().entries()) {
            projectMenu.addItem("Add " + entry.displayName(),
                    () -> world.history().execute(new AddGameObjectCommand(entry.factory().get())));
        }
        projectMenu.addSeparator();
        projectMenu.addItem("Project Settings", () -> toasts.show("Project settings coming soon", 2.0f));
    }

    private void buildViewMenu() {
        viewMenu.clear();
        viewMenu.addItem("Toggle Console", onToggleConsole);
        viewMenu.addItem("Toggle FileSystem", onToggleFileSystem);
    }

    private void buildHelpMenu() {
        helpMenu.clear();
        helpMenu.addItem("About Epysia", () -> toasts.show("Epysia editor 0.1", 3.0f));
    }

    @Override
    public void render(PanelContext context) {
        UiRenderer renderer = context.renderer();
        renderBackground(renderer, context);
        renderMenuBar(context);
        renderPlayControls(context);
        renderRightSection(context);
    }

    private void renderBackground(UiRenderer renderer, PanelContext context) {
        renderer.drawRect(context.x(), context.y(), context.width(), context.height(), EditorStyle.TOPBAR_BG);
        renderer.drawRect(context.x(), context.y() + context.height() - 1, context.width(), 1, EditorStyle.COLOR_SEPARATOR);
    }

    private void renderMenuBar(PanelContext context) {
        int barHeight = Math.min(MENUBAR_HEIGHT, context.height());
        menuBar.renderBar(context.renderer(), context.uiContext(), context.ui().input(), context.ui().theme(),
                context.x(), context.y(), context.width() / 2, barHeight, true);
    }

    public void renderMenuDropdownOverlay(UiRenderer renderer, UiInput input, Theme theme) {
        menuBar.renderDropdown(renderer, input, theme);
    }

    private void renderPlayControls(PanelContext context) {
        int totalWidth = PLAY_BUTTON_SIZE * 3 + PLAY_BUTTON_GAP * 2;
        int startX = context.x() + (context.width() - totalWidth) / 2;
        int y = context.y() + (context.height() - PLAY_BUTTON_SIZE) / 2;
        boolean isPlaying = world.isPlaying();
        Button leading = isPlaying ? pauseButton : playButton;
        if (leading.render(context.renderer(), context.uiContext(), context.ui().input(), context.ui().theme(),
                startX, y, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, true)) {
            world.togglePlay();
        }
        if (stopButton.render(context.renderer(), context.uiContext(), context.ui().input(), context.ui().theme(),
                startX + PLAY_BUTTON_SIZE + PLAY_BUTTON_GAP, y, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, true)) {
            if (world.isPlaying()) {
                world.togglePlay();
            }
        }
    }

    private void renderRightSection(PanelContext context) {
        UiRenderer renderer = context.renderer();
        String stateLabel = world.isPlaying() ? "PLAYING" : "EDIT";
        int stateColor = world.isPlaying() ? EditorStyle.COLOR_PLAY : EditorStyle.COLOR_TEXT_MUTED;
        int baselineY = context.y() + context.height() / 2 + 5;
        int projectWidth = Math.round(renderer.measureText(project.name()));
        int projectX = context.x() + context.width() - RIGHT_SECTION_PADDING - projectWidth;
        renderer.drawText(project.name(), projectX, baselineY, EditorStyle.COLOR_TEXT_PRIMARY);
        int stateWidth = Math.round(renderer.measureText(stateLabel));
        renderer.drawText(stateLabel, projectX - stateWidth - 16, baselineY, stateColor);
    }

    private void performNewScene() {
        toasts.show("New scene scaffolding coming soon", 2.0f);
    }

    private void performSave() {
        try {
            Files.createDirectories(scenePath.getParent());
            fr.epistudio.epysia.gameobjects.GameObject editorCamera = sceneHost.editorCameraObject();
            serializer.save(sceneHost.scene(), scenePath, gameObject -> gameObject != editorCamera);
            toasts.show("Saved " + scenePath.getFileName(), 2.5f);
        } catch (IOException exception) {
            toasts.show("Save failed: " + exception.getMessage(), 4.0f);
        }
    }

    private void performLoad() {
        try {
            serializer.load(sceneHost.scene(), scenePath);
            world.selectIndex(-1);
            toasts.show("Loaded " + scenePath.getFileName(), 2.5f);
        } catch (IOException exception) {
            toasts.show("Load failed: " + exception.getMessage(), 4.0f);
        }
    }

    private void performNewScript() {
        try {
            Files.createDirectories(project.scriptsDirectory());
            Path created = fr.epistudio.epysia.editor.scripts.ScriptScaffold.createNewScript();
            toasts.show("Created " + created.getFileName(), 3.0f);
        } catch (Exception exception) {
            toasts.show("New script failed: " + exception.getMessage(), 4.0f);
        }
    }

    private void duplicateSelected() {
        world.selected().ifPresentOrElse(
                source -> {
                    fr.epistudio.epysia.gameobjects.GameObject copy =
                            new fr.epistudio.epysia.gameobjects.GameObject(source.name() + " copy");
                    source.getComponent(fr.epistudio.epysia.components.transforms.Transform3D.class).ifPresent(transform -> {
                        fr.epistudio.epysia.components.transforms.Transform3D copyTransform =
                                new fr.epistudio.epysia.components.transforms.Transform3D();
                        copyTransform.setPosition(transform.position().x + 1.0f, transform.position().y, transform.position().z);
                        copy.addComponent(copyTransform);
                    });
                    source.getComponent(fr.epistudio.epysia.components.MeshRenderer.class).ifPresent(meshRenderer ->
                            meshRenderer.materialForSlot(0).ifPresent(material ->
                                    copy.addComponent(new fr.epistudio.epysia.components.MeshRenderer()
                                            .setMesh(meshRenderer.mesh()).setMaterial(material))));
                    world.history().execute(new AddGameObjectCommand(copy));
                    toasts.show("Duplicated " + source.name(), 1.5f);
                },
                () -> toasts.show("Nothing selected", 1.5f));
    }

    private void deleteSelected() {
        java.util.List<fr.epistudio.epysia.gameobjects.GameObject> targets = world.selectedAll();
        if (targets.isEmpty()) {
            toasts.show("Nothing selected", 1.5f);
            return;
        }
        java.util.List<fr.epistudio.epysia.editor.command.EditorCommand> removals = new java.util.ArrayList<>(targets.size());
        for (fr.epistudio.epysia.gameobjects.GameObject target : targets) {
            removals.add(new RemoveGameObjectCommand(target));
        }
        if (removals.size() == 1) {
            world.history().execute(removals.get(0));
            toasts.show("Deleted " + targets.get(0).name(), 1.5f);
        } else {
            world.history().execute(new fr.epistudio.epysia.editor.command.CompositeCommand(
                    "Delete " + removals.size() + " objects", removals));
            toasts.show("Deleted " + removals.size() + " objects", 1.5f);
        }
    }

    public boolean isMenuOpen() {
        return menuBar.hasOpenMenu();
    }
}
