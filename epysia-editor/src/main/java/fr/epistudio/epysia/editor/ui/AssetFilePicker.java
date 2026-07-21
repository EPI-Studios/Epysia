package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class AssetFilePicker {

    private static final String POPUP_ID = "Select File";
    private static final int FILTER_CAPACITY = 128;
    private static final float LIST_WIDTH = 460.0f;
    private static final float LIST_HEIGHT = 300.0f;
    private static final float PREVIEW_SIZE = 24.0f;
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp");
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");

    private final Project project;
    private final ThumbnailCache thumbnails;
    private final ImString filterInput = new ImString(FILTER_CAPACITY);
    private List<String> candidates = List.of();
    private Consumer<String> onPicked = path -> {
    };
    private boolean allowClear;
    private boolean openRequested;

    public AssetFilePicker(Project project, ThumbnailCache thumbnails) {
        this.project = project;
        this.thumbnails = thumbnails;
    }

    public void open(Set<String> extensions, boolean clearable, Consumer<String> pickedHandler) {
        onPicked = pickedHandler;
        allowClear = clearable;
        candidates = scanProject(extensions);
        filterInput.set("");
        openRequested = true;
    }

    private List<String> scanProject(Set<String> extensions) {
        try (Stream<Path> walk = Files.walk(project.rootDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path))
                    .filter(path -> matchesExtension(path, extensions))
                    .map(path -> path.toAbsolutePath().toString())
                    .sorted()
                    .toList();
        } catch (IOException error) {
            return List.of();
        }
    }

    private boolean isExcluded(Path path) {
        for (Path segment : project.rootDirectory().relativize(path)) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesExtension(Path path, Set<String> extensions) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(name::endsWith);
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.inputTextWithHint("##file-filter", "Search", filterInput);
        renderCandidateList();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderCandidateList() {
        ImGui.beginChild("##file-candidates", LIST_WIDTH, LIST_HEIGHT, true);
        if (allowClear) {
            renderClearEntry();
        }
        String query = filterInput.get().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (query.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(query)) {
                renderCandidate(candidate);
            }
        }
        ImGui.endChild();
    }

    private void renderClearEntry() {
        ImGui.dummy(PREVIEW_SIZE, PREVIEW_SIZE);
        ImGui.sameLine();
        if (ImGui.selectable("None")) {
            ImGui.closeCurrentPopup();
            onPicked.accept("");
        }
    }

    private void renderCandidate(String candidate) {
        renderPreview(candidate);
        ImGui.sameLine();
        if (ImGui.selectable(displayNameFor(candidate))) {
            ImGui.closeCurrentPopup();
            onPicked.accept(candidate);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(candidate);
        }
    }

    private void renderPreview(String candidate) {
        OptionalInt preview = previewFor(candidate);
        if (preview.isPresent()) {
            ImGui.image(preview.getAsInt(), PREVIEW_SIZE, PREVIEW_SIZE);
        } else {
            ImGui.dummy(PREVIEW_SIZE, PREVIEW_SIZE);
        }
    }

    private OptionalInt previewFor(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (PREVIEWABLE_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            return OptionalInt.empty();
        }
        return thumbnails.get(candidate);
    }

    private String displayNameFor(String candidate) {
        Path path = Path.of(candidate);
        if (path.startsWith(project.rootDirectory())) {
            return project.rootDirectory().relativize(path).toString();
        }
        return candidate;
    }
}
