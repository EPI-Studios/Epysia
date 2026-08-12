package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.editor.icons.IconAtlas;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.notify.ToastCenter;
import fr.epistudio.epysia.editor.runtime.EditorCamera;
import fr.epistudio.epysia.editor.scripts.IdeProjectWriter;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.shell.ImGuiShell;
import fr.epistudio.epysia.editor.ui.EditorView;
import fr.epistudio.epysia.editor.ui.FrameView;
import fr.epistudio.epysia.diagnostics.CrashReporter;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.ui.ProjectSelectorView;
import fr.epistudio.epysia.gpu.GpuLauncher;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectQualityProperties;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import imgui.ImGui;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class EditorMain {

    private static final String IMGUI_LAYOUT_DIRECTORY = ".epysia";
    private static final String IMGUI_LAYOUT_FILENAME = "imgui.ini";

    private final ImGuiShell shell = new ImGuiShell();
    private final ProjectStore projectStore = new ProjectStore();
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
    private final ToastCenter toasts = new ToastCenter();
    private final IconAtlas iconAtlas = new IconAtlas();
    private Path currentLayoutFile;
    private FrameView currentView;
    private EditorScene3DHost activeSceneHost;
    private double lastFrameSeconds;

    public static void main(String[] arguments) {
        CrashReporter.install(EditorPreferences.defaultFile().getParent(),
                new ConsoleLogger(System.err));
        GpuLauncher.enforce(EditorPreferences.load(EditorPreferences.defaultFile()).gpuPreference());
        Optional<ImportRun> importRun = ImportRun.parse(arguments);
        if (importRun.isPresent()) {
            runImport(importRun.get());
            return;
        }
        Optional<ExportRun> export = ExportRun.parse(arguments);
        if (export.isPresent()) {
            runExport(export.get());
            return;
        }
        new EditorMain().run();
    }

    private static void runImport(ImportRun importRun) {
        try {
            importRun.run();
        } catch (IOException failure) {
            System.err.println("[import] failed: " + failure.getMessage());
            throw new UncheckedIOException(failure);
        }
    }

    private static void runExport(ExportRun export) {
        try {
            export.run();
        } catch (IOException failure) {
            System.err.println("[export] failed: " + failure.getMessage());
            throw new UncheckedIOException(failure);
        }
    }

    private void run() {
        EditorPreferences startupPreferences = EditorPreferences.load(EditorPreferences.defaultFile());
        shell.setViewportsEnabled(startupPreferences.detachableWindows());
        shell.setUiScalePreference(startupPreferences.uiScale());
        shell.setFrameRateCap(DEFAULT_FOCUSED_FRAMERATE);
        shell.initialize();
        iconAtlas.loadAll();
        componentRegistry.populateFromScan(ComponentScanner.scan());
        openProjectSelector();
        loop();
        shutdown();
    }

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long UNFOCUSED_FRAME_NANOS = 100L * NANOS_PER_MILLISECOND;
    private static final int DEFAULT_FOCUSED_FRAMERATE = 144;

    private long focusedFrameNanos = 1_000_000_000L / DEFAULT_FOCUSED_FRAMERATE;

    private void loop() {
        lastFrameSeconds = GLFW.glfwGetTime();
        EditorFrameProfiler frameProfiler = new EditorFrameProfiler();
        while (!shell.shouldClose()) {
            double now = GLFW.glfwGetTime();
            float delta = (float) Math.max(0.0, now - lastFrameSeconds);
            lastFrameSeconds = now;
            long frameStart = System.nanoTime();
            shell.beginFrame();
            long pollEnd = System.nanoTime();
            currentView.render(delta);
            toasts.render();
            long viewEnd = System.nanoTime();
            shell.recordUiBuildNanos(viewEnd - pollEnd);
            shell.endFrame();
            frameProfiler.record(frameStart, pollEnd, viewEnd, shell);
            throttle(frameStart);
        }
    }

    private void throttle(long frameStart) {
        boolean active = shell.isFocused() || shell.isInteracting();
        if (active && !shell.isFrameRateCapEnabled()) {
            return;
        }
        long target = active ? focusedFrameNanos : UNFOCUSED_FRAME_NANOS;
        long remaining = target - (System.nanoTime() - frameStart);
        if (remaining <= 0L) {
            return;
        }
        try {
            Thread.sleep(remaining / NANOS_PER_MILLISECOND, (int) (remaining % NANOS_PER_MILLISECOND));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdown() {
        saveCurrentLayout();
        currentView.dispose();
        closeActiveSceneHost();
        iconAtlas.dispose();
        shell.dispose();
    }

    private void openProjectSelector() {
        switchView(new ProjectSelectorView(projectStore, toasts, new IconWidgets(iconAtlas), this::openProject));
        ImGui.getIO().setIniFilename(null);
        currentLayoutFile = null;
    }

    private void openProject(Project project) {
        ProjectQualityProperties.apply(projectStore.readQuality(project));
        IdeProjectWriter.write(project).ifPresent(toasts::show);
        Window embeddedWindow = new Window("(editor-embedded)",
                shell.framebufferWidth(), shell.framebufferHeight());
        EditorScene3DHost sceneHost = new EditorScene3DHost(embeddedWindow, new Scene(project.name()));
        sceneHost.initialize(shell.framebufferWidth(), shell.framebufferHeight());
        activeSceneHost = sceneHost;
        EditorCamera editorCamera = new EditorCamera();
        applyProjectLayoutFile(project);
        switchView(new EditorView(project, componentRegistry, projectStore, sceneHost, editorCamera,
                new IconWidgets(iconAtlas), toasts, shell, this::returnToProjectSelector));
    }

    private void applyProjectLayoutFile(Project project) {
        Path layoutFile = project.rootDirectory().resolve(IMGUI_LAYOUT_DIRECTORY).resolve(IMGUI_LAYOUT_FILENAME);
        try {
            Files.createDirectories(layoutFile.getParent());
        } catch (IOException error) {
            toasts.show(I18n.translate(TextKey.EDITOR_EDITOR_MAIN_TOAST_LAYOUT_DIRECTORY_FAILED,
                    error.getMessage()));
        }
        currentLayoutFile = layoutFile;
        ImGui.getIO().setIniFilename(layoutFile.toString());
        if (Files.exists(layoutFile)) {
            ImGui.loadIniSettingsFromDisk(layoutFile.toString());
        }
    }

    private void saveCurrentLayout() {
        if (currentLayoutFile != null) {
            ImGui.saveIniSettingsToDisk(currentLayoutFile.toString());
        }
    }

    private void returnToProjectSelector() {
        saveCurrentLayout();
        closeActiveSceneHost();
        openProjectSelector();
    }

    private void closeActiveSceneHost() {
        if (activeSceneHost != null) {
            activeSceneHost.shutdown();
            activeSceneHost = null;
        }
    }

    private void switchView(FrameView nextView) {
        if (currentView != null) {
            currentView.dispose();
        }
        currentView = nextView;
    }
}
