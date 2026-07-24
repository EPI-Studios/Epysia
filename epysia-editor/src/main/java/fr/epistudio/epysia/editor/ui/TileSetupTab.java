package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImGui;

import java.util.Optional;

public final class TileSetupTab {

    private static final float CENTER_COLUMN_WIDTH = 300.0f;
    private static final float PREVIEW_SIZE = 240.0f;
    private static final float PLATFORM_HEIGHT = 0.35f;

    private final TileBrush brush;
    private final TilePalettePanel palette;
    private final TileDataSection dataSection;
    private final TileTerrainsSection terrainsSection;
    private final TilePolygonEditor polygonEditor = new TilePolygonEditor();

    public TileSetupTab(TileBrush brush, TilePalettePanel palette,
                        TileDataSection dataSection, TileTerrainsSection terrainsSection) {
        this.brush = brush;
        this.palette = palette;
        this.dataSection = dataSection;
        this.terrainsSection = terrainsSection;
    }

    public boolean render(SpriteTilemap tilemap, SpriteAtlas atlas) {
        boolean changed = renderCollisionColumn(tilemap, atlas);
        ImGui.sameLine();
        ImGui.beginChild("tileSetupRight", 0.0f, 0.0f, true);
        changed |= terrainsSection.render(tilemap);
        changed |= dataSection.render(tilemap);
        ImGui.endChild();
        return changed;
    }

    private boolean renderCollisionColumn(SpriteTilemap tilemap, SpriteAtlas atlas) {
        ImGui.beginChild("tileSetupCenter", CENTER_COLUMN_WIDTH, 0.0f, true);
        ImGui.text("Tile " + brush.tileIndex() + " collision");
        boolean changed = renderPresets(tilemap);
        changed |= renderPolygonEditor(tilemap, atlas);
        ImGui.endChild();
        return changed;
    }

    private boolean renderPresets(SpriteTilemap tilemap) {
        TileData data = tilemap.tileData(brush.tileIndex());
        boolean changed = renderSolidToggle(tilemap, data);
        changed |= presetButton(tilemap, data, "Slope /", TileCollisionShape.slope(true),
                "A ramp climbing to the right.");
        ImGui.sameLine();
        changed |= presetButton(tilemap, data, "Slope \\", TileCollisionShape.slope(false),
                "A ramp climbing to the left.");
        ImGui.sameLine();
        changed |= presetButton(tilemap, data, "Platform", TileCollisionShape.platform(PLATFORM_HEIGHT),
                "A thin band along the top of the cell.");
        ImGui.sameLine();
        return changed | clearButton(tilemap, data);
    }

    private boolean renderSolidToggle(SpriteTilemap tilemap, TileData data) {
        boolean solid = tilemap.isSolidTile(brush.tileIndex()) && data.collisionShapes().isEmpty();
        boolean clicked = ImGui.checkbox("Whole cell is solid", solid);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Full cells merge into large boxes, which is the cheapest collision.\n"
                    + "Adding a polygon below replaces this.");
        }
        if (!clicked) {
            return false;
        }
        data.clearCollisionShapes();
        tilemap.setSolid(brush.tileIndex(), !solid);
        return true;
    }

    private boolean presetButton(SpriteTilemap tilemap, TileData data, String label,
                                 TileCollisionShape shape, String tooltip) {
        boolean clicked = ImGui.button(label);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        if (!clicked) {
            return false;
        }
        data.clearCollisionShapes();
        data.addCollisionShape(shape);
        tilemap.setSolid(brush.tileIndex(), false);
        return true;
    }

    private boolean clearButton(SpriteTilemap tilemap, TileData data) {
        boolean clicked = ImGui.button("No collision");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("This tile stops blocking anything.");
        }
        if (!clicked) {
            return false;
        }
        data.clearCollisionShapes();
        tilemap.setSolid(brush.tileIndex(), false);
        return true;
    }

    private boolean renderPolygonEditor(SpriteTilemap tilemap, SpriteAtlas atlas) {
        Optional<ImagePreviewTexture.PreviewImage> image = palette.atlasImage(tilemap, atlas);
        Optional<SpriteAtlasRegion> region = atlas.region(Integer.toString(brush.tileIndex()));
        if (image.isEmpty() || region.isEmpty()) {
            ImGui.textDisabled("Tile preview unavailable.");
            return false;
        }
        ImGui.textDisabled("Drag a handle to reshape, click an edge to add, right click a handle to remove.");
        return polygonEditor.render(tilemap.tileData(brush.tileIndex()), image.get().textureId(),
                region.get().minU(), region.get().minV(), region.get().maxU(), region.get().maxV(), PREVIEW_SIZE);
    }
}
