package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectStore;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class NewProjectDialog {

    private static final String POPUP_TITLE = "New Project";
    private static final String INVALID_NAME_CHARS = "/\\:*?\"<>|";
    private static final float DIALOG_WIDTH = 520.0f;
    private static final int NAME_CAPACITY = 128;
    private static final int PATH_CAPACITY = 512;

    private final ProjectStore store;
    private final Notifier notifier;
    private final Consumer<Project> onCreated;
    private final ImString nameInput = new ImString(NAME_CAPACITY);
    private final ImString parentInput = new ImString(System.getProperty("user.home"), PATH_CAPACITY);
    private boolean openRequested;

    public NewProjectDialog(ProjectStore store, Notifier notifier, Consumer<Project> onCreated) {
        this.store = store;
        this.notifier = notifier;
        this.onCreated = onCreated;
    }

    public void open() {
        nameInput.set("");
        openRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_TITLE, "new-project-dialog"));
            openRequested = false;
        }
        ImGui.setNextWindowSize(DIALOG_WIDTH, 0.0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_TITLE, "new-project-dialog"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderFields();
        renderValidationAndButtons();
        ImGui.endPopup();
    }

    private void renderFields() {
        ImGui.inputText(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_PROJECT_NAME,
                "new-project-name"), nameInput);
        ImGui.inputText(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_PARENT_FOLDER,
                "new-project-parent"), parentInput);
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_BROWSE,
                "new-project-browse"))) {
            browseParent();
        }
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_FINAL_PATH, previewPath()));
    }

    private void browseParent() {
        Path start = currentParent().filter(Files::isDirectory)
                .orElse(Path.of(System.getProperty("user.home")));
        FileDialogs.pickFolder(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_CHOOSE_PARENT_FOLDER), start)
                .ifPresent(path -> parentInput.set(path.toString()));
    }

    private void renderValidationAndButtons() {
        Optional<String> error = validationError();
        error.ifPresent(message -> ImGui.textColored(EditorStyle.COLOR_DANGER, message));
        ImGui.separator();
        ImGui.beginDisabled(error.isPresent());
        if (ImGui.button(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_CREATE,
                "new-project-create"))) {
            attemptCreate();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_NEW_PROJECT_DIALOG_CANCEL,
                "new-project-cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private void attemptCreate() {
        try {
            Path parent = currentParent().orElseThrow(() -> new IOException("Invalid parent folder"));
            Project project = store.createProject(nameInput.get().trim(), parent.resolve(nameInput.get().trim()));
            store.recordOpened(project);
            ImGui.closeCurrentPopup();
            onCreated.accept(project);
        } catch (IOException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_TOAST_CREATION_FAILED,
                    error.getMessage()));
        }
    }

    private String previewPath() {
        String name = nameInput.get().trim();
        return currentParent().filter(parent -> !name.isEmpty())
                .map(parent -> parent.resolve(name).toString())
                .orElse("-");
    }

    private Optional<Path> currentParent() {
        String raw = parentInput.get().trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(raw));
        } catch (InvalidPathException error) {
            return Optional.empty();
        }
    }

    private Optional<String> validationError() {
        String name = nameInput.get().trim();
        if (name.isEmpty()) {
            return Optional.of(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_ERROR_NAME_REQUIRED));
        }
        if (name.chars().anyMatch(character -> INVALID_NAME_CHARS.indexOf(character) >= 0)) {
            return Optional.of(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_ERROR_FORBIDDEN_CHARACTERS));
        }
        return parentValidationError(name);
    }

    private Optional<String> parentValidationError(String name) {
        Optional<Path> parent = currentParent();
        if (parent.isEmpty()) {
            return Optional.of(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_ERROR_PARENT_REQUIRED));
        }
        if (!Files.isDirectory(parent.get())) {
            return Optional.of(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_ERROR_PARENT_MUST_EXIST));
        }
        if (Files.exists(parent.get().resolve(name))) {
            return Optional.of(I18n.translate(TextKey.EDITOR_NEW_PROJECT_DIALOG_ERROR_FOLDER_EXISTS));
        }
        return Optional.empty();
    }
}
