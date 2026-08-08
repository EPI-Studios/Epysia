package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.export.ExportRequest;
import fr.epistudio.epysia.editor.export.ExportStage;
import fr.epistudio.epysia.editor.export.ExportTask;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.export.GameExporter;
import fr.epistudio.epysia.editor.export.TargetPlatform;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExportGameDialog {

    private static final String POPUP_ID = "Export Game";
    private static final int TITLE_CAPACITY = 128;
    private static final float FIELD_WIDTH = 360.0f;
    private static final float PERCENT = 100.0f;

    private final Project project;
    private final Notifier notifier;
    private final ImString titleInput = new ImString(TITLE_CAPACITY);
    private Path iconFile;
    private final List<String> sceneFileNames = new ArrayList<>();
    private final TargetPlatform[] platforms = TargetPlatform.values();
    private final ExportTask exportTask = new ExportTask();
    private int selectedSceneIndex;
    private int selectedPlatformIndex;
    private Path outputDirectory;
    private boolean openRequested;
    private boolean closeRequested;

    public ExportGameDialog(Project project, Notifier notifier) {
        this.project = project;
        this.notifier = notifier;
    }

    public void open(String activeSceneName) {
        titleInput.set(project.name());
        outputDirectory = null;
        closeRequested = false;
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
            notifier.show(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_COULD_NOT_LIST_SCENES,
                    error.getMessage()));
        }
    }

    private void addScene(String fileName, String activeSceneName) {
        if (fileName.equals(activeSceneName + Project.SCENE_EXTENSION)) {
            selectedSceneIndex = sceneFileNames.size();
        }
        sceneFileNames.add(fileName);
    }

    public void render() {
        announceOutcome();
        if (openRequested) {
            ImGui.openPopup(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_TITLE, "export-game-dialog"));
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_TITLE, "export-game-dialog"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderBody();
        ImGui.endPopup();
    }

    private void renderBody() {
        if (closeRequested) {
            closeRequested = false;
            ImGui.closeCurrentPopup();
            return;
        }
        if (exportTask.isRunning()) {
            renderProgress();
            return;
        }
        renderFields();
        ImGui.separator();
        renderButtons();
    }

    private void renderProgress() {
        ImGui.textUnformatted(I18n.translate(stageKey(exportTask.stage())));
        float completion = exportTask.completion();
        ImGui.progressBar(completion, FIELD_WIDTH, 0.0f, Math.round(completion * PERCENT) + "%");
    }

    private static TextKey stageKey(ExportStage stage) {
        return switch (stage) {
            case DOWNLOADING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_DOWNLOADING_TEMPLATE;
            case UNPACKING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_UNPACKING_TEMPLATE;
            case COPYING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_COPYING_TEMPLATE;
            case COPYING_PROJECT -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_COPYING_PROJECT;
            case WRITING_LAUNCHER -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_WRITING_LAUNCHER;
            case ARCHIVING -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_ARCHIVING;
        };
    }

    private void renderFields() {
        ImGui.setNextItemWidth(FIELD_WIDTH);
        ImGui.inputText(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_GAME_TITLE,
                "export-game-title"), titleInput);
        renderSceneCombo();
        renderPlatformCombo();
        renderIconRow();
        renderOutputRow();
    }

    private void renderIconRow() {
        if (ImGui.button("Choose icon...###export-game-icon")) {
            FileDialogs.pickFile("Game icon", project.rootDirectory(), "*.png", "PNG image")
                    .ifPresent(path -> iconFile = path);
        }
        ImGui.sameLine();
        ImGui.textDisabled(iconFile == null ? "Default Epysia icon" : iconFile.getFileName().toString());
        if (iconFile != null) {
            ImGui.sameLine();
            if (ImGui.button("Clear###export-game-icon-clear")) {
                iconFile = null;
            }
        }
    }

    private void renderPlatformCombo() {
        ImGui.setNextItemWidth(FIELD_WIDTH);
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_PLATFORM,
                "export-game-platform"), I18n.translate(platformKey(platforms[selectedPlatformIndex])))) {
            return;
        }
        for (int index = 0; index < platforms.length; index++) {
            if (ImGui.selectable(I18n.label(platformKey(platforms[index]),
                    "export-game-platform-" + platforms[index].name()), index == selectedPlatformIndex)) {
                selectedPlatformIndex = index;
            }
        }
        ImGui.endCombo();
    }

    private static TextKey platformKey(TargetPlatform platform) {
        return switch (platform) {
            case WINDOWS -> TextKey.EDITOR_TARGET_PLATFORM_WINDOWS;
            case LINUX -> TextKey.EDITOR_TARGET_PLATFORM_LINUX;
        };
    }

    private void renderSceneCombo() {
        String preview = sceneFileNames.isEmpty()
                ? I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_NO_SCENES)
                : sceneFileNames.get(selectedSceneIndex);
        ImGui.setNextItemWidth(FIELD_WIDTH);
        if (!ImGui.beginCombo(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_SCENE,
                "export-game-scene"), preview)) {
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
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CHOOSE_FOLDER,
                "export-game-choose-folder"))) {
            FileDialogs.pickFolder(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_DESTINATION),
                            project.rootDirectory().getParent())
                    .ifPresent(path -> outputDirectory = path);
        }
        ImGui.sameLine();
        ImGui.textDisabled(outputDirectory == null
                ? I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_NO_FOLDER_SELECTED)
                : outputDirectory.toString());
    }

    private void renderButtons() {
        boolean ready = outputDirectory != null && !sceneFileNames.isEmpty()
                && !titleInput.get().trim().isEmpty();
        ImGui.beginDisabled(!ready);
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_EXPORT, "export-game-export"))) {
            startExport();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CANCEL, "export-game-cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private void startExport() {
        ExportRequest request = new ExportRequest(outputDirectory, titleInput.get().trim(),
                sceneFileNames.get(selectedSceneIndex), platforms[selectedPlatformIndex],
                EditorPreferences.load(EditorPreferences.defaultFile()).gpuPreference(),
                Optional.ofNullable(iconFile));
        exportTask.start(new GameExporter(project), request);
    }

    private void announceOutcome() {
        exportTask.drainOutcome().ifPresent(outcome -> {
            outcome.destination().ifPresent(destination -> notifier.show(
                    I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_GAME_EXPORTED, destination)));
            outcome.failure().ifPresent(message -> notifier.show(
                    I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_EXPORT_FAILED, message)));
            closeRequested = true;
        });
    }
}
