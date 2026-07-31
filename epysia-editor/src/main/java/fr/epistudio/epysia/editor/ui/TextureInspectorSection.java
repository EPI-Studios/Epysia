package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.assets.loaders.TextureImportSettings;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureWrap;
import imgui.ImGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class TextureInspectorSection {

    private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final float PREVIEW_MAX_HEIGHT = 220.0f;

    private final ImagePreviewTexture preview;
    private final Consumer<Path> onImportSettingsChanged;

    public TextureInspectorSection(ImagePreviewTexture preview, Consumer<Path> onImportSettingsChanged) {
        this.preview = preview;
        this.onImportSettingsChanged = onImportSettingsChanged;
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
        renderImportSettings(path);
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

    private void renderImportSettings(Path path) {
        TextureImportSettings settings = settingsOf(path);
        renderFilterCombo(path, settings);
        renderWrapCombo(path, settings);
        renderColorSpaceCheckbox(path, settings);
        renderMipmapCheckbox(path, settings);
        renderAnisotropySlider(path, settings);
    }

    private void renderMipmapCheckbox(Path path, TextureImportSettings settings) {
        if (ImGui.checkbox("Mipmaps", settings.mipmaps())) {
            apply(path, TextureImportSettings.MIPMAPS_KEY, Boolean.toString(!settings.mipmaps()));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Off by default for point filtering, on for linear. "
                    + "Without them a surface seen at an angle shimmers.");
        }
    }

    private void renderAnisotropySlider(Path path, TextureImportSettings settings) {
        if (!settings.mipmaps()) {
            return;
        }
        int[] level = {settings.anisotropy()};
        if (ImGui.sliderInt("Anisotropy", level, 1, TextureImportSettings.MAXIMUM_ANISOTROPY)) {
            apply(path, TextureImportSettings.ANISOTROPY_KEY, Integer.toString(level[0]));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Clamped to what the GPU reports. 1 disables it.");
        }
    }

    private static TextureImportSettings settingsOf(Path path) {
        return TextureImportSettings.from(AssetMetaFile.settingsOf(path), AssetVariant.none());
    }

    private void renderFilterCombo(Path path, TextureImportSettings settings) {
        boolean point = settings.filter() == SamplerFilter.NEAREST;
        if (!ImGui.beginCombo("Filter", point ? "Point" : "Linear")) {
            return;
        }
        if (ImGui.selectable("Linear", !point) && point) {
            apply(path, TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_LINEAR);
        }
        if (ImGui.selectable("Point", point) && !point) {
            apply(path, TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_POINT);
        }
        ImGui.endCombo();
    }

    private void renderWrapCombo(Path path, TextureImportSettings settings) {
        String current = wrapName(settings.wrap());
        if (!ImGui.beginCombo("Wrap", current)) {
            return;
        }
        for (String candidate : List.of(TextureImportSettings.WRAP_REPEAT,
                TextureImportSettings.WRAP_CLAMP, TextureImportSettings.WRAP_MIRROR)) {
            if (ImGui.selectable(candidate, candidate.equals(current)) && !candidate.equals(current)) {
                apply(path, TextureImportSettings.WRAP_KEY, candidate);
            }
        }
        ImGui.endCombo();
    }

    private static String wrapName(TextureWrap wrap) {
        return switch (wrap) {
            case CLAMP_TO_EDGE -> TextureImportSettings.WRAP_CLAMP;
            case MIRRORED_REPEAT -> TextureImportSettings.WRAP_MIRROR;
            case REPEAT -> TextureImportSettings.WRAP_REPEAT;
        };
    }

    private void renderColorSpaceCheckbox(Path path, TextureImportSettings settings) {
        boolean srgb = settings.format() == TextureFormat.SRGB8_ALPHA8;
        if (ImGui.checkbox("sRGB", srgb)) {
            apply(path, TextureImportSettings.COLOR_SPACE_KEY, srgb
                    ? TextureImportSettings.COLOR_SPACE_LINEAR : TextureImportSettings.COLOR_SPACE_SRGB);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Colour space is applied when the texture is uploaded. Reopen the scene to see it.");
        }
    }

    private void apply(Path path, String key, String value) {
        AssetMetaFile.writeString(AssetMetaFile.pathFor(path), key, value);
        onImportSettingsChanged.accept(path);
    }
}
