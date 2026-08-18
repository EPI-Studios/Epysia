package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.assets.EditorAssetPaths;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.assets.AssetScheme;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
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
import fr.epistudio.epysia.assets.AssetDatabase;
import java.util.Optional;
import java.util.function.Supplier;

public final class AssetFilePicker {

    private static final String POPUP_ID = "Select File";
    private static final int FILTER_CAPACITY = 128;
    private static final float LIST_WIDTH = 720.0f;
    private static final float LIST_HEIGHT = 440.0f;
    private static final float PREVIEW_SIZE = 88.0f;
    private static final float TILE_PADDING = 12.0f;
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp", ".epynoise", ".epygradient", ".epycurve");
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

    private Supplier<Optional<AssetDatabase>> database =
            Optional::empty;

    public AssetFilePicker(Project project, ThumbnailCache thumbnails) {
        this.project = project;
        this.thumbnails = thumbnails;
    }

    public void useDatabase(
            Supplier<Optional<AssetDatabase>> source) {
        this.database = source;
    }

    public void open(Set<String> extensions, boolean clearable, Consumer<String> pickedHandler) {
        onPicked = pickedHandler;
        allowClear = clearable;
        candidates = scanProject(extensions);
        filterInput.set("");
        openRequested = true;
    }

    private List<String> scanProject(Set<String> extensions) {
        List<String> indexed = fromDatabase(extensions);
        if (!indexed.isEmpty()) {
            return indexed;
        }
        return walkProject(extensions);
    }

    private List<String> fromDatabase(Set<String> extensions) {
        return database.get().map(index -> index.paths().stream()
                        .filter(path -> matchesExtension(Path.of(path), extensions))
                        .map(path -> AssetUri.project(path).toString())
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    private List<String> walkProject(Set<String> extensions) {
        try (Stream<Path> walk = Files.walk(project.rootDirectory())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path))
                    .filter(path -> matchesExtension(path, extensions))
                    .map(path -> project.locator().fromFile(path).toString())
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
            ImGui.openPopup(I18n.label(TextKey.EDITOR_ASSET_FILE_PICKER_TITLE, "asset-file-picker"));
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_ASSET_FILE_PICKER_TITLE, "asset-file-picker"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.inputTextWithHint("##file-filter",
                I18n.translate(TextKey.EDITOR_ASSET_FILE_PICKER_SEARCH), filterInput);
        renderCandidateList();
        if (ImGui.button(I18n.label(TextKey.EDITOR_ASSET_FILE_PICKER_CANCEL, "asset-file-picker-cancel"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderCandidateList() {
        ImGui.beginChild("##file-candidates", EditorScale.of(LIST_WIDTH), EditorScale.of(LIST_HEIGHT), true);
        if (allowClear) {
            renderClearEntry();
        }
        String query = filterInput.get().toLowerCase(Locale.ROOT);
        float available = ImGui.getContentRegionAvailX();
        float used = 0.0f;
        for (String candidate : candidates) {
            if (!query.isEmpty() && !candidate.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (used > 0.0f && used + EditorScale.of(PREVIEW_SIZE) + EditorScale.of(TILE_PADDING) < available) {
                ImGui.sameLine();
                used += EditorScale.of(PREVIEW_SIZE) + EditorScale.of(TILE_PADDING);
            } else {
                used = EditorScale.of(PREVIEW_SIZE) + EditorScale.of(TILE_PADDING);
            }
            renderCandidate(candidate);
        }
        ImGui.endChild();
    }

    private void renderClearEntry() {
        if (ImGui.button(I18n.label(TextKey.EDITOR_ASSET_FILE_PICKER_NONE, "asset-file-picker-none"),
                EditorScale.of(PREVIEW_SIZE), 0.0f)) {
            ImGui.closeCurrentPopup();
            onPicked.accept("");
        }
    }

    private void renderCandidate(String candidate) {
        ImGui.pushID(candidate);
        ImGui.beginGroup();
        if (renderPreviewButton(candidate)) {
            ImGui.closeCurrentPopup();
            onPicked.accept(candidate);
        }
        ImGui.textUnformatted(shortNameFor(candidate));
        ImGui.endGroup();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(displayNameFor(candidate));
        }
        ImGui.popID();
    }

    private boolean renderPreviewButton(String candidate) {
        OptionalInt preview = previewFor(candidate);
        if (preview.isPresent()) {
            return ImGui.imageButton("##asset-preview", preview.getAsInt(), EditorScale.of(PREVIEW_SIZE),
                    EditorScale.of(PREVIEW_SIZE));
        }
        return ImGui.button(fileExtensionOf(candidate), EditorScale.of(PREVIEW_SIZE), EditorScale.of(PREVIEW_SIZE));
    }

    private OptionalInt previewFor(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (PREVIEWABLE_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            return OptionalInt.empty();
        }
        return thumbnails.get(EditorAssetPaths.absolute(project.locator(), candidate));
    }

    private static String shortNameFor(String candidate) {
        String name = candidate.substring(candidate.lastIndexOf('/') + 1);
        return name.length() <= 13 ? name : name.substring(0, 12) + "\u2026";
    }

    private static String fileExtensionOf(String candidate) {
        int dot = candidate.lastIndexOf('.');
        return dot < 0 ? "?" : candidate.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private String displayNameFor(String candidate) {
        AssetUri uri = LegacyAssetReferences.interpretWithoutMigration(candidate, project.locator());
        return uri.scheme() == AssetScheme.PROJECT ? uri.path() : candidate;
    }
}
