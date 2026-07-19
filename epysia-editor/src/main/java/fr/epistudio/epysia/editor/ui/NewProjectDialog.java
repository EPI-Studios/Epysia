package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.editor.shell.FileDialogs;
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
            ImGui.openPopup(POPUP_TITLE);
            openRequested = false;
        }
        ImGui.setNextWindowSize(DIALOG_WIDTH, 0.0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(POPUP_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderFields();
        renderValidationAndButtons();
        ImGui.endPopup();
    }

    private void renderFields() {
        ImGui.inputText("Project name", nameInput);
        ImGui.inputText("Parent folder", parentInput);
        ImGui.sameLine();
        if (ImGui.button("Browse")) {
            browseParent();
        }
        ImGui.textDisabled("Final path: " + previewPath());
    }

    private void browseParent() {
        Path start = currentParent().filter(Files::isDirectory)
                .orElse(Path.of(System.getProperty("user.home")));
        FileDialogs.pickFolder("Choose a parent folder", start)
                .ifPresent(path -> parentInput.set(path.toString()));
    }

    private void renderValidationAndButtons() {
        Optional<String> error = validationError();
        error.ifPresent(message -> ImGui.textColored(EditorStyle.COLOR_DANGER, message));
        ImGui.separator();
        ImGui.beginDisabled(error.isPresent());
        if (ImGui.button("Create")) {
            attemptCreate();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
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
            notifier.show("Creation failed: " + error.getMessage());
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
            return Optional.of("A project name is required.");
        }
        if (name.chars().anyMatch(character -> INVALID_NAME_CHARS.indexOf(character) >= 0)) {
            return Optional.of("Forbidden characters: / \\ : * ? \" < > |");
        }
        return parentValidationError(name);
    }

    private Optional<String> parentValidationError(String name) {
        Optional<Path> parent = currentParent();
        if (parent.isEmpty()) {
            return Optional.of("A parent folder is required.");
        }
        if (!Files.isDirectory(parent.get())) {
            return Optional.of("The parent folder must exist.");
        }
        if (Files.exists(parent.get().resolve(name))) {
            return Optional.of("A folder with this name already exists.");
        }
        return Optional.empty();
    }
}
