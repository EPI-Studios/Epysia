package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
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
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class AssetPicker {

    private static final String POPUP_ID = "Pick Asset";
    private static final int FILTER_CAPACITY = 128;
    private static final float LIST_HEIGHT = 280.0f;
    private static final Set<String> MESH_EXTENSIONS = Set.of(".obj", ".epymesh");
    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".bmp");
    private static final Set<String> ATLAS_EXTENSIONS = Set.of(".epyatlas");
    private static final Set<String> INSTANCES_EXTENSIONS = Set.of(".epyinstances");
    private static final Set<String> MATERIAL_EXTENSIONS = Set.of(".epymaterial");
    private static final List<String> MESH_PRESETS = List.of("preset:cube", "preset:plane", "preset:capsule",
            "preset:sphere", "preset:quad", "preset:unitQuad");
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");

    private final Project project;
    private final ImString filterInput = new ImString(FILTER_CAPACITY);
    private List<String> candidates = List.of();
    private Consumer<String> onPicked = path -> {
    };
    private boolean openRequested;

    public AssetPicker(Project project) {
        this.project = project;
    }

    public void open(Class<?> assetType, Consumer<String> pickedHandler) {
        onPicked = pickedHandler;
        candidates = collectCandidates(assetType);
        filterInput.set("");
        openRequested = true;
    }

    public void open(Set<String> extensions, Consumer<String> pickedHandler) {
        onPicked = pickedHandler;
        candidates = scanProject(extensions);
        filterInput.set("");
        openRequested = true;
    }

    private List<String> collectCandidates(Class<?> assetType) {
        String mimeType = AssetMimeTypes.forAssetType(assetType);
        List<String> result = new ArrayList<>();
        if (AssetMimeTypes.MESH.equals(mimeType)) {
            result.addAll(MESH_PRESETS);
        }
        result.addAll(scanProject(extensionsFor(mimeType)));
        return result;
    }

    private static Set<String> extensionsFor(String mimeType) {
        if (AssetMimeTypes.MESH.equals(mimeType)) {
            return MESH_EXTENSIONS;
        }
        if (AssetMimeTypes.TEXTURE.equals(mimeType)) {
            return TEXTURE_EXTENSIONS;
        }
        if (AssetMimeTypes.ATLAS.equals(mimeType)) {
            return ATLAS_EXTENSIONS;
        }
        if (AssetMimeTypes.INSTANCES.equals(mimeType)) {
            return INSTANCES_EXTENSIONS;
        }
        if (AssetMimeTypes.MATERIAL.equals(mimeType)) {
            return MATERIAL_EXTENSIONS;
        }
        return Set.of();
    }

    private List<String> scanProject(Set<String> extensions) {
        if (extensions.isEmpty()) {
            return List.of();
        }
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
            ImGui.openPopup(I18n.label(TextKey.EDITOR_ASSET_PICKER_TITLE, "asset-picker"));
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_ASSET_PICKER_TITLE, "asset-picker"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.inputText(I18n.label(TextKey.EDITOR_ASSET_PICKER_FILTER, "asset-picker-filter"), filterInput);
        renderCandidateList();
        if (ImGui.button(I18n.label(TextKey.EDITOR_ASSET_PICKER_CANCEL, "asset-picker-cancel"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderCandidateList() {
        ImGui.beginChild("##asset-candidates", 460.0f, EditorScale.of(LIST_HEIGHT), true);
        String query = filterInput.get().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (query.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(query)) {
                renderCandidate(candidate);
            }
        }
        ImGui.endChild();
    }

    private void renderCandidate(String candidate) {
        if (ImGui.selectable(candidate)) {
            ImGui.closeCurrentPopup();
            onPicked.accept(candidate);
        }
    }
}
