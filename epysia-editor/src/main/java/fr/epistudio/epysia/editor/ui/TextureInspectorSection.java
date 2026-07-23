package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import imgui.ImGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class TextureInspectorSection {

    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final String FILTER_LINEAR = "linear";
    private static final String FILTER_POINT = "point";
    private static final float PREVIEW_MAX_HEIGHT = 220.0f;

    private final ImagePreviewTexture preview;
    private final Consumer<Path> onFilterChanged;

    public TextureInspectorSection(ImagePreviewTexture preview, Consumer<Path> onFilterChanged) {
        this.preview = preview;
        this.onFilterChanged = onFilterChanged;
    }

    public boolean render(Optional<Path> selectedAsset) {
        Optional<Path> texturePath = selectedAsset.filter(TextureInspectorSection::isTextureFile);
        if (texturePath.isEmpty()) {
            return false;
        }
        Path path = texturePath.get();
        ImGui.textUnformatted(path.getFileName().toString());
        ImGui.separator();
        renderPreview(path);
        renderFilterCombo(path);
        return true;
    }

    private static boolean isTextureFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && TEXTURE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private void renderPreview(Path path) {
        Optional<ImagePreviewTexture.PreviewImage> image = preview.get(path);
        if (image.isEmpty()) {
            ImGui.textDisabled("Preview unavailable");
            return;
        }
        ImGui.textDisabled(image.get().width() + " x " + image.get().height() + " px");
        float width = Math.max(32.0f, ImGui.getContentRegionAvailX());
        float height = width * image.get().height() / image.get().width();
        if (height > PREVIEW_MAX_HEIGHT) {
            width = width * PREVIEW_MAX_HEIGHT / height;
            height = PREVIEW_MAX_HEIGHT;
        }
        ImGui.image(image.get().textureId(), width, height);
    }

    private void renderFilterCombo(Path path) {
        boolean point = isPointFiltered(path);
        if (!ImGui.beginCombo("Filter", point ? "Point" : "Linear")) {
            return;
        }
        if (ImGui.selectable("Linear", !point) && point) {
            applyFilter(path, FILTER_LINEAR);
        }
        if (ImGui.selectable("Point", point) && !point) {
            applyFilter(path, FILTER_POINT);
        }
        ImGui.endCombo();
    }

    private static boolean isPointFiltered(Path path) {
        return AssetMetaFile.readString(AssetMetaFile.pathFor(path), AssetMetaFile.FILTER_KEY)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .map(name -> name.equals(FILTER_POINT) || name.equals("nearest"))
                .orElse(false);
    }

    private void applyFilter(Path path, String filterName) {
        AssetMetaFile.writeString(AssetMetaFile.pathFor(path), AssetMetaFile.FILTER_KEY, filterName);
        onFilterChanged.accept(path);
    }
}
