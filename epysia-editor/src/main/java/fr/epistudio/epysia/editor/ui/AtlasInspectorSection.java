package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import imgui.ImGui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class AtlasInspectorSection {

    public static final String EXTENSION = ".epyatlas";

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();
    private Optional<SpriteAtlas> cachedAtlas = Optional.empty();
    private String cachedPath = "";
    private long cachedModifiedMillis;
    private String cachedError = "";

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
            ImGui.textDisabled("Could not parse atlas: " + cachedError);
            return;
        }
        SpriteAtlas atlas = cachedAtlas.get();
        ImGui.textDisabled("Texture");
        ImGui.textUnformatted(atlas.texturePath().isEmpty() ? "(none)" : atlas.texturePath());
        atlas.grid().ifPresent(AtlasInspectorSection::renderGrid);
        renderRegions(atlas);
    }

    private static void renderGrid(SpriteAtlasGrid grid) {
        ImGui.textDisabled("Grid");
        ImGui.textUnformatted(grid.columns() + " x " + grid.rows() + " cells ("
                + grid.cellWidth() + " x " + grid.cellHeight() + " px)");
    }

    private static void renderRegions(SpriteAtlas atlas) {
        ImGui.textDisabled(atlas.regionCount() + " regions");
        ImGui.beginChild("##atlas-regions", 0.0f, 0.0f, true);
        for (String name : atlas.regionNames()) {
            atlas.region(name).ifPresent(AtlasInspectorSection::renderRegion);
        }
        ImGui.endChild();
    }

    private static void renderRegion(SpriteAtlasRegion region) {
        ImGui.textUnformatted(region.name());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("uv [%.3f, %.3f] - [%.3f, %.3f]",
                region.minU(), region.minV(), region.maxU(), region.maxV()));
    }

    private static long modifiedMillisOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
