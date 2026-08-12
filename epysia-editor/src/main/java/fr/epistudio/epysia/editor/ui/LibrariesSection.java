package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scripts.LibraryResolutionTask;
import fr.epistudio.epysia.editor.shell.FileDialogs;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectDependencies;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.jar.JarFile;

public final class LibrariesSection {

    private static final String PICK_TITLE = "Add library";
    private static final String PICK_PATTERN = "*" + ProjectLibraries.ARCHIVE_SUFFIX;
    private static final String PICK_DESCRIPTION = "Jar archives";
    private static final long BYTES_PER_KILOBYTE = 1024L;
    private static final int COORDINATE_CAPACITY = 160;
    private static final float COORDINATE_INPUT_WIDTH = 320.0f;

    private final Notifier notifier;
    private final Runnable onLibrariesChanged;
    private final LibraryResolutionTask resolution = new LibraryResolutionTask();
    private final ImString coordinateInput = new ImString(COORDINATE_CAPACITY);
    private Optional<Path> pendingRemoval = Optional.empty();

    public LibrariesSection(Notifier notifier, Runnable onLibrariesChanged) {
        this.notifier = notifier;
        this.onLibrariesChanged = onLibrariesChanged;
    }

    public void render(Project project) {
        drainResolution();
        renderLimits();
        renderDependencies(project);
        ImGui.separator();
        renderArchives(project);
    }

    private void renderArchives(Project project) {
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_JARS));
        if (ImGui.button(PICK_TITLE)) {
            addLibrary(project);
        }
        ProjectLibraries libraries = project.libraries();
        if (libraries.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_NO_JAR, Project.LIBRARIES_DIRECTORY_NAME));
            return;
        }
        libraries.archives().forEach(this::renderRow);
    }

    private void renderDependencies(Project project) {
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_MAVEN_COORDINATES));
        ImGui.setNextItemWidth(EditorScale.of(COORDINATE_INPUT_WIDTH));
        ImGui.inputTextWithHint("##coordinate", "group:artifact:version", coordinateInput);
        ImGui.sameLine();
        if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_ADD))) {
            addCoordinate(project);
        }
        ImGui.sameLine();
        renderResolveButton(project);
        project.dependencies().coordinates().forEach(coordinate -> renderCoordinateRow(project, coordinate));
    }

    private void renderResolveButton(Project project) {
        if (resolution.isRunning()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_RESOLVING));
            return;
        }
        if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_RESOLVE))) {
            resolution.start(project);
        }
    }

    private void renderCoordinateRow(Project project, String coordinate) {
        ImGui.pushID(coordinate);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(coordinate);
        ImGui.sameLine();
        if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_REMOVE))) {
            writeDependencies(project, project.dependencies().without(coordinate));
        }
        ImGui.popID();
    }

    private void addCoordinate(Project project) {
        String coordinate = coordinateInput.get().trim();
        if (!ProjectDependencies.isWellFormed(coordinate)) {
            notifier.show("Not a Maven coordinate: " + coordinate);
            return;
        }
        writeDependencies(project, project.dependencies().with(coordinate));
        coordinateInput.set("");
    }

    private void writeDependencies(Project project, ProjectDependencies dependencies) {
        try {
            dependencies.writeTo(project.dependenciesFile());
        } catch (IOException error) {
            notifier.show("Could not write " + Project.DEPENDENCIES_FILENAME + ": " + error.getMessage());
        }
    }

    private void drainResolution() {
        resolution.drainOutcome().ifPresent(outcome -> {
            outcome.messages().forEach(notifier::show);
            if (outcome.ok()) {
                onLibrariesChanged.run();
            }
        });
    }

    private void renderLimits() {
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_JARS_HINT, Project.LIBRARIES_DIRECTORY_NAME));
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_MAVEN_HINT,
                Project.LIBRARIES_CACHE_DIRECTORY_NAME));
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_HAND_DROPPED_HINT));
        Texts.muted(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_COLLISION_HINT));
    }

    private void renderRow(Path archive) {
        ImGui.pushID(archive.toString());
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(archive.getFileName() + "   " + kilobytesOf(archive) + " KB");
        ImGui.sameLine();
        if (isPendingRemoval(archive)) {
            renderRemovalConfirmation(archive);
        } else if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_REMOVE))) {
            pendingRemoval = Optional.of(archive);
        }
        ImGui.popID();
    }

    private void renderRemovalConfirmation(Path archive) {
        if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_CONFIRM))) {
            removeLibrary(archive);
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.translate(TextKey.EDITOR_LIBRARIES_SECTION_CANCEL))) {
            pendingRemoval = Optional.empty();
        }
    }

    private boolean isPendingRemoval(Path archive) {
        return pendingRemoval.filter(archive::equals).isPresent();
    }

    private void addLibrary(Project project) {
        Optional<Path> picked = FileDialogs.pickFile(PICK_TITLE, project.rootDirectory(),
                PICK_PATTERN, PICK_DESCRIPTION);
        if (picked.isEmpty()) {
            return;
        }
        Path source = picked.get();
        Optional<String> rejection = rejectionFor(project, source);
        if (rejection.isPresent()) {
            notifier.show(rejection.get());
            return;
        }
        copyIntoLibraries(project, source);
    }

    private static Optional<String> rejectionFor(Project project, Path source) {
        if (!ProjectLibraries.isArchive(source)) {
            return Optional.of(source.getFileName() + " is not a " + ProjectLibraries.ARCHIVE_SUFFIX + " file.");
        }
        if (Files.exists(project.librariesDirectory().resolve(source.getFileName().toString()))) {
            return Optional.of(source.getFileName() + " is already in "
                    + Project.LIBRARIES_DIRECTORY_NAME + "/.");
        }
        return readabilityRejection(source);
    }

    private static Optional<String> readabilityRejection(Path source) {
        try (JarFile ignored = new JarFile(source.toFile())) {
            return Optional.empty();
        } catch (IOException error) {
            return Optional.of(source.getFileName() + " is not a readable jar: " + error.getMessage());
        }
    }

    private void copyIntoLibraries(Project project, Path source) {
        try {
            Files.createDirectories(project.librariesDirectory());
            Files.copy(source, project.librariesDirectory().resolve(source.getFileName().toString()),
                    StandardCopyOption.REPLACE_EXISTING);
            onLibrariesChanged.run();
            notifier.show("Added library " + source.getFileName() + ".");
        } catch (IOException error) {
            notifier.show("Could not add " + source.getFileName() + ": " + error.getMessage());
        }
    }

    private void removeLibrary(Path archive) {
        pendingRemoval = Optional.empty();
        try {
            Files.delete(archive);
            onLibrariesChanged.run();
            notifier.show("Removed library " + archive.getFileName() + ".");
        } catch (IOException error) {
            notifier.show("Could not remove " + archive.getFileName() + ": " + error.getMessage());
        }
    }

    private static long kilobytesOf(Path archive) {
        try {
            return Math.max(1L, Files.size(archive) / BYTES_PER_KILOBYTE);
        } catch (IOException error) {
            return 0L;
        }
    }
}
