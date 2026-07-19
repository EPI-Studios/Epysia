package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.export.ExportRequest;
import fr.epistudio.epysia.editor.export.GameExporter;
import fr.epistudio.epysia.editor.export.TargetPlatform;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ExportGameDialog {

    private static final String POPUP_ID = "Export Game";
    private static final int TITLE_CAPACITY = 128;
    private static final float FIELD_WIDTH = 360.0f;

    private final Project project;
    private final Notifier notifier;
    private final ImString titleInput = new ImString(TITLE_CAPACITY);
    private final List<String> sceneFileNames = new ArrayList<>();
    private final TargetPlatform[] platforms = TargetPlatform.values();
    private int selectedSceneIndex;
    private int selectedPlatformIndex;
    private Path outputDirectory;
    private boolean openRequested;

    public ExportGameDialog(Project project, Notifier notifier) {
        this.project = project;
        this.notifier = notifier;
    }

    public void open(String activeSceneName) {
        titleInput.set(project.name());
        outputDirectory = null;
        refreshScenes(activeSceneName);
        openRequested = true;
    }

    private void refreshScenes(String activeSceneName) {
        sceneFileNames.clear();
        selectedSceneIndex = 0;
        try (var stream = Files.newDirectoryStream(project.scenesDirectory(),
                "*" + Project.SCENE_EXTENSION)) {
            for (Path scene : stream) {
                addScene(scene.getFileName().toString(), activeSceneName);
            }
        } catch (IOException error) {
            notifier.show("Could not list scenes: " + error.getMessage());
        }
    }

    private void addScene(String fileName, String activeSceneName) {
        if (fileName.equals(activeSceneName + Project.SCENE_EXTENSION)) {
            selectedSceneIndex = sceneFileNames.size();
        }
        sceneFileNames.add(fileName);
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderFields();
        ImGui.separator();
        renderButtons();
        ImGui.endPopup();
    }

    private void renderFields() {
        ImGui.setNextItemWidth(FIELD_WIDTH);
        ImGui.inputText("Game title", titleInput);
        renderSceneCombo();
        renderPlatformCombo();
        renderOutputRow();
    }

    private void renderPlatformCombo() {
        ImGui.setNextItemWidth(FIELD_WIDTH);
        if (!ImGui.beginCombo("Platform", platforms[selectedPlatformIndex].displayName())) {
            return;
        }
        for (int index = 0; index < platforms.length; index++) {
            if (ImGui.selectable(platforms[index].displayName(), index == selectedPlatformIndex)) {
                selectedPlatformIndex = index;
            }
        }
        ImGui.endCombo();
    }

    private void renderSceneCombo() {
        String preview = sceneFileNames.isEmpty() ? "(no scenes)" : sceneFileNames.get(selectedSceneIndex);
        ImGui.setNextItemWidth(FIELD_WIDTH);
        if (!ImGui.beginCombo("Scene", preview)) {
            return;
        }
        for (int index = 0; index < sceneFileNames.size(); index++) {
            if (ImGui.selectable(sceneFileNames.get(index), index == selectedSceneIndex)) {
                selectedSceneIndex = index;
            }
        }
        ImGui.endCombo();
    }

    private void renderOutputRow() {
        if (ImGui.button("Choose folder…")) {
            FileDialogs.pickFolder("Export destination", project.rootDirectory().getParent())
                    .ifPresent(path -> outputDirectory = path);
        }
        ImGui.sameLine();
        ImGui.textDisabled(outputDirectory == null ? "No folder selected" : outputDirectory.toString());
    }

    private void renderButtons() {
        boolean ready = outputDirectory != null && !sceneFileNames.isEmpty()
                && !titleInput.get().trim().isEmpty();
        ImGui.beginDisabled(!ready);
        if (ImGui.button("Export")) {
            runExport();
            ImGui.closeCurrentPopup();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void runExport() {
        ExportRequest request = new ExportRequest(outputDirectory, titleInput.get().trim(),
                sceneFileNames.get(selectedSceneIndex), platforms[selectedPlatformIndex]);
        try {
            Path destination = new GameExporter(project).export(request);
            notifier.show("Game exported to " + destination);
        } catch (IOException error) {
            notifier.show("Export failed: " + error.getMessage());
        }
    }
}
