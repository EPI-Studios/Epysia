package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.assets.epytilemap.TileNeighbor;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.util.Optional;

public final class TileDataSection {

    private static final float BIT_BUTTON_SIZE = 22.0f;
    private static final float TILE_ZONE_EXTENT = 132.0f;
    private static final int ZONE_BORDER_COLOUR = 0x60FFFFFF;
    private static final int CENTRE_BORDER_COLOUR = 0xFFFFFFFF;
    private static final int INACTIVE_ZONE_COLOUR = 0x90101010;

    private static int zoneColour(org.joml.Vector4f colour, float alpha) {
        int red = Math.clamp(Math.round(colour.x * 255.0f), 0, 255);
        int green = Math.clamp(Math.round(colour.y * 255.0f), 0, 255);
        int blue = Math.clamp(Math.round(colour.z * 255.0f), 0, 255);
        int opacity = Math.clamp(Math.round(alpha * 255.0f), 0, 255);
        return opacity << 24 | blue << 16 | green << 8 | red;
    }
    private static final int KEY_CAPACITY = 48;
    private static final int PATH_CAPACITY = 260;
    private static final float PLATFORM_HEIGHT = 0.35f;

    private final TileBrush brush;
    private final ImString customKey = new ImString(KEY_CAPACITY);
    private final ImString customValue = new ImString(KEY_CAPACITY);
    private final ImString scenePath = new ImString(PATH_CAPACITY);
    private final ImFloat probability = new ImFloat(1.0f);
    private Optional<TilePreview> preview = Optional.empty();

    public TileDataSection(TileBrush brush) {
        this.brush = brush;
    }

    public record TilePreview(int textureId, float minU, float minV, float maxU, float maxV) {
    }

    public boolean render(SpriteTilemap tilemap) {
        return render(tilemap, Optional.empty());
    }

    public boolean render(SpriteTilemap tilemap, Optional<TilePreview> preview) {
        this.preview = preview;
        if (!ImGui.collapsingHeader("Tile " + brush.tileIndex())) {
            return false;
        }
        TileData data = tilemap.tileData(brush.tileIndex());
        boolean changed = renderSolidToggle(tilemap);
        changed |= renderCollisionPresets(tilemap, data);
        changed |= renderOrientation(tilemap, data);
        changed |= renderProbability(tilemap, data);
        changed |= renderTerrainBits(tilemap, data);
        changed |= renderScenePath(tilemap);
        changed |= renderCustomData(tilemap, data);
        return changed;
    }

    private boolean renderSolidToggle(SpriteTilemap tilemap) {
        boolean solid = tilemap.isSolidTile(brush.tileIndex());
        boolean clicked = ImGui.checkbox("Solid (full cell)", solid);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Blocks as a whole square. Cheapest collision, merged into big boxes.");
        }
        if (!clicked) {
            return false;
        }
        tilemap.setSolid(brush.tileIndex(), !solid);
        return true;
    }

    private boolean renderCollisionPresets(SpriteTilemap tilemap, TileData data) {
        ImGui.textDisabled("Collision shapes: " + data.collisionShapes().size());
        boolean changed = addShapeButton(tilemap, data, "Slope /", TileCollisionShape.slope(true));
        ImGui.sameLine();
        changed |= addShapeButton(tilemap, data, "Slope \\", TileCollisionShape.slope(false));
        ImGui.sameLine();
        changed |= addShapeButton(tilemap, data, "Platform", TileCollisionShape.platform(PLATFORM_HEIGHT));
        ImGui.sameLine();
        if (ImGui.button("Clear##shapes")) {
            data.clearCollisionShapes();
            tilemap.touch();
            changed = true;
        }
        return changed;
    }

    private boolean addShapeButton(SpriteTilemap tilemap, TileData data, String label, TileCollisionShape shape) {
        if (!ImGui.button(label)) {
            return false;
        }
        data.addCollisionShape(shape);
        tilemap.setSolid(brush.tileIndex(), true);
        tilemap.touch();
        return true;
    }

    private boolean renderOrientation(SpriteTilemap tilemap, TileData data) {
        boolean changed = orientationToggle("Flip H", data.flipHorizontal(),
                "Mirror this tile left to right everywhere it is painted.", data::setFlipHorizontal);
        ImGui.sameLine();
        changed |= orientationToggle("Flip V", data.flipVertical(),
                "Mirror this tile top to bottom.", data::setFlipVertical);
        ImGui.sameLine();
        changed |= orientationToggle("Transpose", data.transpose(),
                "Mirror across the diagonal, which rotates a corner tile.", data::setTranspose);
        return touchIfChanged(tilemap, changed);
    }

    private static boolean orientationToggle(String label, boolean current, String tooltip,
                                             java.util.function.Consumer<Boolean> assign) {
        boolean clicked = ImGui.checkbox(label, current);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        if (clicked) {
            assign.accept(!current);
        }
        return clicked;
    }

    private boolean renderProbability(SpriteTilemap tilemap, TileData data) {
        probability.set(data.probability());
        ImGui.setNextItemWidth(120.0f);
        boolean edited = ImGui.inputFloat("Probability", probability);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Relative weight when a terrain brush has several matching tiles.\n"
                    + "Lower it to make a variant rare.");
        }
        if (!edited) {
            return false;
        }
        data.setProbability(probability.get());
        tilemap.touch();
        return true;
    }

    private boolean renderTerrainBits(SpriteTilemap tilemap, TileData data) {
        if (tilemap.terrains().isEmpty()) {
            ImGui.textDisabled("Add a terrain to enable autotiling for this tile.");
            return false;
        }
        ImGui.textDisabled("Terrain bits");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Click the middle of the tile to say which terrain it is.\n"
                    + "Click an edge or a corner to say what it connects to there.\n"
                    + "Right click clears. Greyed zones are off in this match mode.");
        }
        boolean changed = renderFillRow(tilemap, data);
        if (preview.isPresent()) {
            return touchIfChanged(tilemap, changed | renderBitsOverTile(tilemap, data, preview.orElseThrow()));
        }
        for (int row = 0; row < 3; row++) {
            changed |= renderBitRow(tilemap, data, row);
        }
        return touchIfChanged(tilemap, changed);
    }

    private boolean renderFillRow(SpriteTilemap tilemap, TileData data) {
        boolean fill = ImGui.button("Fill whole tile");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Sets the centre and every active neighbour to the selected terrain.\n"
                    + "This is the solid interior tile of a terrain.");
        }
        ImGui.sameLine();
        boolean clear = ImGui.button("Clear tile");
        if (fill) {
            assignWholeTile(tilemap, data, brush.terrainIndex());
        }
        if (clear) {
            assignWholeTile(tilemap, data, TileData.NO_TERRAIN);
        }
        return fill || clear;
    }

    private static void assignWholeTile(SpriteTilemap tilemap, TileData data, int terrain) {
        data.setTerrain(terrain);
        for (TileNeighbor neighbor : TileNeighbor.values()) {
            if (neighbor.matches(tilemap.terrainMatchMode())) {
                data.setPeeringTerrain(neighbor, terrain);
            }
        }
    }

    private boolean renderBitsOverTile(SpriteTilemap tilemap, TileData data, TilePreview image) {
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.image(image.textureId(), TILE_ZONE_EXTENT, TILE_ZONE_EXTENT,
                image.minU(), image.minV(), image.maxU(), image.maxV());
        boolean changed = false;
        float cell = TILE_ZONE_EXTENT / 3.0f;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                changed |= renderZone(tilemap, data, row, column, originX + column * cell, originY + row * cell, cell);
            }
        }
        ImGui.setCursorScreenPos(originX, originY + TILE_ZONE_EXTENT + 4.0f);
        return changed;
    }

    private boolean renderZone(SpriteTilemap tilemap, TileData data, int row, int column,
                               float x, float y, float cell) {
        boolean centre = row == 1 && column == 1;
        TileNeighbor neighbor = centre ? null : neighborAt(row, column);
        boolean active = centre || neighbor.matches(tilemap.terrainMatchMode());
        int current = centre ? data.terrain() : data.peeringTerrain(neighbor);
        paintZone(tilemap, x, y, cell, current, active, centre);
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton("zone" + row + column, cell, cell);
        if (!active) {
            return false;
        }
        return applyZoneClick(data, neighbor, centre);
    }

    private boolean applyZoneClick(TileData data, TileNeighbor neighbor, boolean centre) {
        if (ImGui.isItemClicked(0)) {
            assignZone(data, neighbor, centre, brush.terrainIndex());
            return true;
        }
        if (ImGui.isItemClicked(1)) {
            assignZone(data, neighbor, centre, TileData.NO_TERRAIN);
            return true;
        }
        return false;
    }

    private static void assignZone(TileData data, TileNeighbor neighbor, boolean centre, int terrain) {
        if (centre) {
            data.setTerrain(terrain);
            return;
        }
        data.setPeeringTerrain(neighbor, terrain);
    }

    private static void paintZone(SpriteTilemap tilemap, float x, float y, float cell,
                                  int terrain, boolean active, boolean centre) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        if (terrain != TileData.NO_TERRAIN && terrain < tilemap.terrains().size()) {
            drawList.addRectFilled(x, y, x + cell, y + cell,
                    zoneColour(tilemap.terrains().get(terrain).color(), centre ? 0.65f : 0.45f));
        } else if (!active) {
            drawList.addRectFilled(x, y, x + cell, y + cell, INACTIVE_ZONE_COLOUR);
        }
        drawList.addRect(x, y, x + cell, y + cell, centre ? CENTRE_BORDER_COLOUR : ZONE_BORDER_COLOUR);
    }

    private boolean renderBitRow(SpriteTilemap tilemap, TileData data, int row) {
        boolean changed = false;
        for (int column = 0; column < 3; column++) {
            changed |= renderBitCell(tilemap, data, row, column);
            ImGui.sameLine();
        }
        ImGui.newLine();
        return changed;
    }

    private boolean renderBitCell(SpriteTilemap tilemap, TileData data, int row, int column) {
        if (row == 1 && column == 1) {
            return renderCenterBit(tilemap, data);
        }
        TileNeighbor neighbor = neighborAt(row, column);
        ImGui.beginDisabled(!neighbor.matches(tilemap.terrainMatchMode()));
        boolean changed = renderBitButton("bit" + neighbor.name(), data.peeringTerrain(neighbor),
                terrain -> data.setPeeringTerrain(neighbor, terrain));
        ImGui.endDisabled();
        return changed;
    }

    private boolean renderCenterBit(SpriteTilemap tilemap, TileData data) {
        return renderBitButton("bitCenter", data.terrain(), data::setTerrain);
    }

    private boolean renderBitButton(String id, int currentTerrain, java.util.function.IntConsumer assign) {
        ImGui.pushID(id);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,
                currentTerrain == TileData.NO_TERRAIN ? 0xFF303030 : 0xFF4CAF50);
        boolean clicked = ImGui.button(currentTerrain == TileData.NO_TERRAIN ? " " : Integer.toString(currentTerrain),
                BIT_BUTTON_SIZE, BIT_BUTTON_SIZE);
        ImGui.popStyleColor();
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setTooltip(currentTerrain == TileData.NO_TERRAIN
                    ? "No terrain here. Left click to assign the selected one."
                    : "Terrain " + currentTerrain + ". Right click to clear.");
        }
        boolean cleared = hovered && ImGui.isMouseClicked(imgui.flag.ImGuiMouseButton.Right);
        ImGui.popID();
        if (clicked) {
            assign.accept(brush.terrainIndex());
        } else if (cleared) {
            assign.accept(TileData.NO_TERRAIN);
        }
        return clicked || cleared;
    }

    private static TileNeighbor neighborAt(int row, int column) {
        return switch (row * 3 + column) {
            case 0 -> TileNeighbor.TOP_LEFT;
            case 1 -> TileNeighbor.TOP;
            case 2 -> TileNeighbor.TOP_RIGHT;
            case 3 -> TileNeighbor.LEFT;
            case 5 -> TileNeighbor.RIGHT;
            case 6 -> TileNeighbor.BOTTOM_LEFT;
            case 7 -> TileNeighbor.BOTTOM;
            default -> TileNeighbor.BOTTOM_RIGHT;
        };
    }

    private boolean renderScenePath(SpriteTilemap tilemap) {
        scenePath.set(tilemap.sceneForTile(brush.tileIndex()).orElse(""));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean committed = ImGui.inputTextWithHint("##tileScene", "Prefab spawned on this tile", scenePath,
                imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Path to an .epyprefab. Add a Tilemap Scene Spawner to the object\n"
                    + "and every cell painted with this tile becomes that prefab at load.");
        }
        if (!committed) {
            return false;
        }
        tilemap.setSceneForTile(brush.tileIndex(), scenePath.get());
        return true;
    }

    private boolean renderCustomData(SpriteTilemap tilemap, TileData data) {
        data.customData().forEach((key, value) -> ImGui.textDisabled(key + " = " + value));
        ImGui.setNextItemWidth(90.0f);
        ImGui.inputText("##customKey", customKey);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Free form data your scripts can read with renderer.tileValueAt(x, y, key).");
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(90.0f);
        ImGui.inputText("##customValue", customValue);
        ImGui.sameLine();
        if (!ImGui.button("Set##custom") || customKey.get().isBlank()) {
            return false;
        }
        data.setCustomValue(customKey.get(), customValue.get());
        tilemap.touch();
        return true;
    }

    private static boolean touchIfChanged(SpriteTilemap tilemap, boolean changed) {
        if (changed) {
            tilemap.touch();
        }
        return changed;
    }
}
