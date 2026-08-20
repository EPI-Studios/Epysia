package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.icons.ProjectIcons;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.IconCropDialog;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.editor.ui.files.FileBrowser;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ProjectIconSection {

    private static final float PREVIEW_SIZE = 48.0f;
    private static final java.util.Set<String> ICON_EXTENSIONS = java.util.Set.of(".png");

    private final FileBrowser browser;
    private final ProjectIcons icons = new ProjectIcons();
    private final IconCropDialog cropDialog;
    private Path pending = Path.of("");

    public ProjectIconSection(IconWidgets iconWidgets) {
        this.cropDialog = new IconCropDialog(iconWidgets, this::onWritten, this::onFailed);
        this.browser = new FileBrowser(iconWidgets);
    }
    private String message = "";

    public void render(Project project) {
        Optional<Integer> texture = icons.of(project.rootDirectory());
        float size = EditorScale.of(PREVIEW_SIZE);
        texture.ifPresent(id -> ImGui.image(id, size, size));
        if (texture.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_SETTINGS_DIALOG_PROJECT_ICON_NONE));
        }
        if (ImGui.button(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_CHOOSE_ICON))) {
            choose(project);
        }
        browser.render();
        if (texture.isPresent()) {
            ImGui.sameLine();
            if (ImGui.button(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_REMOVE_ICON))) {
                remove(project);
            }
        }
        if (!message.isEmpty()) {
            Texts.muted(message);
        }
        cropDialog.render();
    }

    private void onWritten(Path written) {
        icons.forget(pending);
        message = I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_ICON_SET, written);
    }

    private void onFailed(String failure) {
        message = failure;
    }

    public void dispose() {
        cropDialog.dispose();
        icons.dispose();
    }

    private void choose(Project project) {
        browser.chooseFile(I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_PICK_ICON_TITLE),
                project.rootDirectory(), ICON_EXTENSIONS, source -> write(project, source));
    }

    private void write(Project project, Path source) {
        pending = project.rootDirectory();
        cropDialog.open(source, project.rootDirectory()
                .resolve(ProjectIcons.CANDIDATE_FILENAMES.getFirst()));
    }

    private void remove(Project project) {
        try {
            for (String candidate : ProjectIcons.CANDIDATE_FILENAMES) {
                Files.deleteIfExists(project.rootDirectory().resolve(candidate));
            }
            icons.forget(project.rootDirectory());
        } catch (IOException error) {
            message = I18n.translate(TextKey.EDITOR_PROJECT_SELECTOR_VIEW_TOAST_ICON_FAILED,
                    error.getMessage());
        }
    }
}
