package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasRegion;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.editor.assets.ImagePreviewTexture;
import fr.epistudio.epysia.editor.assets.SpriteOpaqueBounds;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImGui;
import imgui.type.ImInt;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class TileSetupTab {

    private static final float CENTER_COLUMN_WIDTH = 300.0f;
    private static final float PREVIEW_SIZE = 240.0f;
    private static final float PLATFORM_HEIGHT = 0.35f;
    private static final float SNAP_FIELD_WIDTH = 120.0f;
    private static final int MAXIMUM_SNAP_DIVISIONS = 128;

    private final TileBrush brush;
    private final TilePalettePanel palette;
    private final TileDataSection dataSection;
    private final TileTerrainsSection terrainsSection;
    private final TilePolygonEditor polygonEditor = new TilePolygonEditor();
    private final SpriteOpaqueBounds opaqueBounds = new SpriteOpaqueBounds();
    private final ImInt snapDivisions = new ImInt();
    private boolean snapInitialised;

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
        changed |= dataSection.render(tilemap, tilePreview(tilemap, atlas));
        ImGui.endChild();
        return changed;
    }

    private Optional<TileDataSection.TilePreview> tilePreview(SpriteTilemap tilemap, SpriteAtlas atlas) {
        Optional<ImagePreviewTexture.PreviewImage> image = palette.atlasImage(tilemap, atlas);
        Optional<SpriteAtlasRegion> region = atlas.region(Integer.toString(brush.tileIndex()));
        if (image.isEmpty() || region.isEmpty()) {
            return Optional.empty();
        }
        SpriteAtlasRegion bounds = region.orElseThrow();
        return Optional.of(new TileDataSection.TilePreview(image.orElseThrow().textureId(),
                bounds.minU(), bounds.minV(), bounds.maxU(), bounds.maxV()));
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
        boolean changed = renderFitRow(tilemap, atlas);
        changed |= renderShapeOperations(tilemap);
        renderSnapControl(atlas);
        ImGui.textDisabled("Drag a handle, click an edge to add one, right click one to remove it."
                + " Hold Shift to ignore the grid.");
        return changed | polygonEditor.render(tilemap.tileData(brush.tileIndex()), image.get().textureId(),
                region.get().minU(), region.get().minV(), region.get().maxU(), region.get().maxV(),
                PREVIEW_SIZE, snapDivisions.get());
    }

    private boolean renderFitRow(SpriteTilemap tilemap, SpriteAtlas atlas) {
        boolean changed = fitButton(tilemap, atlas, "Fit to pixels",
                "Wrap the opaque pixels of this tile exactly.", false);
        ImGui.sameLine();
        changed |= fitButton(tilemap, atlas, "Fit every tile",
                "Do the same for every tile of the atlas at once.", true);
        return changed;
    }

    private boolean fitButton(SpriteTilemap tilemap, SpriteAtlas atlas, String label,
                              String tooltip, boolean everyTile) {
        boolean clicked = ImGui.button(label);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        if (!clicked) {
            return false;
        }
        return everyTile ? fitEveryTile(tilemap, atlas) : fitTile(tilemap, atlas, brush.tileIndex());
    }

    private boolean fitEveryTile(SpriteTilemap tilemap, SpriteAtlas atlas) {
        SpriteAtlasGrid grid = atlas.grid().orElse(null);
        if (grid == null) {
            return false;
        }
        boolean changed = false;
        for (int tileIndex = 0; tileIndex < grid.columns() * grid.rows(); tileIndex++) {
            changed |= fitTile(tilemap, atlas, tileIndex);
        }
        return changed;
    }

    private boolean fitTile(SpriteTilemap tilemap, SpriteAtlas atlas, int tileIndex) {
        Optional<SpriteOpaqueBounds.UnitBounds> bounds = boundsOf(tilemap, atlas, tileIndex);
        if (bounds.isEmpty()) {
            return false;
        }
        TileData data = tilemap.tileData(tileIndex);
        data.clearCollisionShapes();
        if (bounds.get().fillsCell()) {
            tilemap.setSolid(tileIndex, true);
            return true;
        }
        data.addCollisionShape(TileShapeOperations.rectangleFrom(bounds.get()));
        tilemap.setSolid(tileIndex, false);
        return true;
    }

    private Optional<SpriteOpaqueBounds.UnitBounds> boundsOf(SpriteTilemap tilemap, SpriteAtlas atlas, int tileIndex) {
        Optional<SpriteAtlasGrid> grid = atlas.grid();
        Optional<Path> texture = palette.atlasTextureFile(tilemap, atlas);
        if (grid.isEmpty() || texture.isEmpty()) {
            return Optional.empty();
        }
        return opaqueBounds.boundsOf(texture.get(), grid.get().columns(), grid.get().rows(), tileIndex);
    }

    private boolean renderShapeOperations(SpriteTilemap tilemap) {
        TileData data = tilemap.tileData(brush.tileIndex());
        boolean changed = operationButton("Flip H##shape", "Mirror the shapes left to right.",
                () -> TileShapeOperations.flipHorizontally(data));
        ImGui.sameLine();
        changed |= operationButton("Flip V##shape", "Mirror the shapes top to bottom.",
                () -> TileShapeOperations.flipVertically(data));
        ImGui.sameLine();
        changed |= operationButton("Rotate##shape", "Turn the shapes a quarter turn clockwise.",
                () -> TileShapeOperations.rotateRight(data));
        return changed;
    }

    private static boolean operationButton(String label, String tooltip, BooleanSupplier operation) {
        boolean clicked = ImGui.button(label);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        return clicked && operation.getAsBoolean();
    }

    private void renderSnapControl(SpriteAtlas atlas) {
        atlas.grid().ifPresent(grid -> defaultSnapFrom(grid));
        ImGui.setNextItemWidth(SNAP_FIELD_WIDTH);
        ImGui.inputInt("Snap steps", snapDivisions);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Handles land on this many steps across the tile."
                    + "\nSet it to the tile pixel size for pixel perfect shapes, or zero to snap freely.");
        }
        snapDivisions.set(Math.clamp(snapDivisions.get(), 0, MAXIMUM_SNAP_DIVISIONS));
    }

    private void defaultSnapFrom(SpriteAtlasGrid grid) {
        if (snapInitialised) {
            return;
        }
        snapInitialised = true;
        snapDivisions.set(Math.clamp(grid.cellHeight(), 0, MAXIMUM_SNAP_DIVISIONS));
    }
}
