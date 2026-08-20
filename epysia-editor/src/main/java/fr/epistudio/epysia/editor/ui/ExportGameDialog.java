package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.export.ExportRequest;
import fr.epistudio.epysia.editor.export.ExportStage;
import fr.epistudio.epysia.editor.export.ExportTask;
import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.export.GameExporter;
import fr.epistudio.epysia.editor.export.GameTemplateRepository;
import fr.epistudio.epysia.editor.BuildInfo;
import fr.epistudio.epysia.editor.export.TargetPlatform;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.project.ReleaseSettings;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.ui.files.FileBrowser;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ExportGameDialog {

    private static final String POPUP_ID = "Export Game";
    private static final int TITLE_CAPACITY = 128;
    private static final int VERSION_CAPACITY = 32;
    private static final float VERSION_FIELD_WIDTH = 140.0f;
    private static final float FIELD_WIDTH = 360.0f;
    private static final float PERCENT = 100.0f;

    private final Project project;
    private final Notifier notifier;
    private final ImString titleInput = new ImString(TITLE_CAPACITY);
    private final ImString versionInput = new ImString(VERSION_CAPACITY);
    private final ProjectStore projectStore = new ProjectStore();
    private final FileBrowser browser;
    private Path iconFile;
    private Path templateArchive;
    private boolean prefetching;
    private final List<String> sceneFileNames = new ArrayList<>();
    private final TargetPlatform[] platforms = TargetPlatform.values();
    private final ExportTask exportTask = new ExportTask();
    private int selectedSceneIndex;
    private int selectedPlatformIndex;
    private Path outputDirectory;
    private boolean openRequested;
    private boolean closeRequested;

    public ExportGameDialog(Project project, Notifier notifier, IconWidgets icons) {
        this.browser = new FileBrowser(icons);
        this.project = project;
        this.notifier = notifier;
    }

    public void open(String activeSceneName) {
        titleInput.set(project.name());
        versionInput.set(projectStore.readRelease(project).version());
        templateArchive = null;
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
        browser.render();
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
        ImGui.progressBar(completion, EditorScale.of(FIELD_WIDTH), 0.0f, Math.round(completion * PERCENT) + "%");
    }

    private static TextKey stageKey(ExportStage stage) {
        return switch (stage) {
            case DOWNLOADING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_DOWNLOADING_TEMPLATE;
            case UNPACKING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_UNPACKING_TEMPLATE;
            case COPYING_TEMPLATE -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_COPYING_TEMPLATE;
            case COPYING_PROJECT -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_COPYING_PROJECT;
            case WRITING_LAUNCHER -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_WRITING_LAUNCHER;
            case ARCHIVING -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_ARCHIVING;
            case VALIDATING -> TextKey.EDITOR_EXPORT_GAME_DIALOG_STAGE_VALIDATING;
        };
    }

    private void renderFields() {
        ImGui.setNextItemWidth(EditorScale.of(FIELD_WIDTH));
        ImGui.inputText(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_GAME_TITLE,
                "export-game-title"), titleInput);
        renderVersionRow();
        renderSceneCombo();
        renderPlatformCombo();
        renderIconRow();
        renderTemplateRow();
        renderOutputRow();
    }

    private void renderVersionRow() {
        ImGui.setNextItemWidth(EditorScale.of(VERSION_FIELD_WIDTH));
        ImGui.inputText(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_VERSION,
                "export-game-version"), versionInput);
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_BUMP_VERSION,
                "export-game-version-bump"))) {
            versionInput.set(new ReleaseSettings(versionInput.get()).incremented().version());
        }
    }

    private void renderIconRow() {
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CHOOSE_ICON, "export-game-icon"))) {
            browser.chooseFile(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_PICK_ICON_TITLE),
                    project.rootDirectory(), Set.of(".png"), path -> iconFile = path);
        }
        ImGui.sameLine();
        Texts.muted(iconFile == null ? "Default Epysia icon" : iconFile.getFileName().toString());
        if (iconFile != null) {
            ImGui.sameLine();
            if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CLEAR_ICON, "export-game-icon-clear"))) {
                iconFile = null;
            }
        }
    }

    private void renderTemplateRow() {
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CHOOSE_TEMPLATE,
                "export-game-template"))) {
            browser.chooseFile(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_PICK_TEMPLATE_TITLE),
                    project.rootDirectory(), Set.of(".zip"), path -> templateArchive = path);
        }
        ImGui.sameLine();
        Texts.muted(templateArchive == null
                ? I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_DEFAULT_TEMPLATE)
                : templateArchive.getFileName().toString());
        renderTemplateActions();
    }

    private void renderTemplateActions() {
        if (templateArchive != null) {
            ImGui.sameLine();
            if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_CLEAR_TEMPLATE,
                    "export-game-template-clear"))) {
                templateArchive = null;
            }
            return;
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_EXPORT_GAME_DIALOG_PREFETCH_TEMPLATES,
                "export-game-template-prefetch"))) {
            startPrefetch();
        }
    }

    private void startPrefetch() {
        prefetching = true;
        BuildInfo buildInfo = BuildInfo.load();
        GameTemplateRepository repository = new GameTemplateRepository();
        exportTask.start(() -> repository.prefetch(buildInfo.version(), buildInfo.repository(),
                exportTask));
    }

    private void renderPlatformCombo() {
        ImGui.setNextItemWidth(EditorScale.of(FIELD_WIDTH));
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
        ImGui.setNextItemWidth(EditorScale.of(FIELD_WIDTH));
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
            browser.chooseFolder(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_DESTINATION),
                    project.rootDirectory().getParent(), path -> outputDirectory = path);
        }
        ImGui.sameLine();
        Texts.muted(outputDirectory == null
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
        prefetching = false;
        rememberVersion();
        ExportRequest request = new ExportRequest(outputDirectory, titleInput.get().trim(),
                versionInput.get().trim(), sceneFileNames.get(selectedSceneIndex),
                platforms[selectedPlatformIndex],
                EditorPreferences.load(EditorPreferences.defaultFile()).gpuPreference(),
                Optional.ofNullable(iconFile), Optional.ofNullable(templateArchive));
        exportTask.start(new GameExporter(project), request);
    }

    private void rememberVersion() {
        ReleaseSettings release = new ReleaseSettings(versionInput.get()).sanitized();
        versionInput.set(release.version());
        try {
            projectStore.writeRelease(project, release);
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_EXPORT_FAILED,
                    error.getMessage()));
        }
    }

    private void announceOutcome() {
        exportTask.drainOutcome().ifPresent(outcome -> {
            outcome.destination().ifPresent(destination -> notifier.show(I18n.translate(
                    prefetching
                            ? TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_TEMPLATES_READY
                            : TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_GAME_EXPORTED, destination)));
            outcome.failure().ifPresent(message -> notifier.show(
                    I18n.translate(TextKey.EDITOR_EXPORT_GAME_DIALOG_TOAST_EXPORT_FAILED, message)));
            closeRequested = !prefetching;
            prefetching = false;
        });
    }
}
