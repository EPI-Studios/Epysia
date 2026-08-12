package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class AtlasInspectorSection {

    public static final String EXTENSION = ".epyatlas";

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();
    private final Consumer<Path> onOpenSpriteEditor;
    private Optional<SpriteAtlas> cachedAtlas = Optional.empty();
    private String cachedPath = "";
    private long cachedModifiedMillis;
    private String cachedError = "";

    public AtlasInspectorSection(Consumer<Path> onOpenSpriteEditor) {
        this.onOpenSpriteEditor = onOpenSpriteEditor;
    }

    public boolean render(Optional<Path> selectedAsset) {
        Optional<Path> atlasPath = selectedAsset.filter(path ->
                path.getFileName().toString().endsWith(EXTENSION) && Files.isRegularFile(path));
        if (atlasPath.isEmpty()) {
            return false;
        }
        Path path = atlasPath.get();
        refreshCache(path);
        renderContent(path);
        return true;
    }

    private void refreshCache(Path path) {
        long modifiedMillis = modifiedMillisOf(path);
        if (path.toString().equals(cachedPath) && modifiedMillis == cachedModifiedMillis) {
            return;
        }
        cachedPath = path.toString();
        cachedModifiedMillis = modifiedMillis;
        try {
            cachedAtlas = Optional.of(codec.read(Files.readString(path)));
            cachedError = "";
        } catch (IOException | RuntimeException unreadable) {
            cachedAtlas = Optional.empty();
            cachedError = unreadable.getMessage() == null ? "unreadable atlas" : unreadable.getMessage();
        }
    }

    private void renderContent(Path path) {
        ImGui.textUnformatted(path.getFileName().toString());
        ImGui.separator();
        if (cachedAtlas.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_ATLAS_PARSE_ERROR, cachedError));
            return;
        }
        renderSummary(cachedAtlas.get());
        if (ImGui.button(I18n.translate(TextKey.EDITOR_ATLAS_INSPECTOR_SECTION_OPEN_SPRITE_EDITOR), ImGui.getContentRegionAvailX(), 0.0f)) {
            onOpenSpriteEditor.accept(path);
        }
    }

    private static void renderSummary(SpriteAtlas atlas) {
        Texts.muted(I18n.translate(TextKey.EDITOR_ATLAS_INSPECTOR_SECTION_TEXTURE));
        ImGui.textUnformatted(atlas.texturePath().isEmpty() ? "(none)" : atlas.texturePath());
        Texts.muted(atlas.regionCount() + " regions, " + atlas.animations().size() + " animations");
        for (String name : atlas.animationNames()) {
            atlas.animation(name).ifPresent(animation -> ImGui.textUnformatted(animation.name() + "  "
                    + animation.frames().size() + " frames @ " + animation.framesPerSecond() + " fps"));
        }
    }

    private static long modifiedMillisOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
