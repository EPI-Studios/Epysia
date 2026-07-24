package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.assets.epytilemap.TileNeighbor;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImString;

public final class TileDataSection {

    private static final float BIT_BUTTON_SIZE = 22.0f;
    private static final int KEY_CAPACITY = 48;
    private static final float PLATFORM_HEIGHT = 0.35f;

    private final TileBrush brush;
    private final ImString customKey = new ImString(KEY_CAPACITY);
    private final ImString customValue = new ImString(KEY_CAPACITY);
    private final ImFloat probability = new ImFloat(1.0f);

    public TileDataSection(TileBrush brush) {
        this.brush = brush;
    }

    public boolean render(SpriteTilemap tilemap) {
        if (!ImGui.collapsingHeader("Tile " + brush.tileIndex())) {
            return false;
        }
        TileData data = tilemap.tileData(brush.tileIndex());
        boolean changed = renderSolidToggle(tilemap);
        changed |= renderCollisionPresets(tilemap, data);
        changed |= renderOrientation(tilemap, data);
        changed |= renderProbability(tilemap, data);
        changed |= renderTerrainBits(tilemap, data);
        changed |= renderCustomData(tilemap, data);
        return changed;
    }

    private boolean renderSolidToggle(SpriteTilemap tilemap) {
        boolean solid = tilemap.isSolidTile(brush.tileIndex());
        if (!ImGui.checkbox("Solid (full cell)", solid)) {
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
        boolean changed = false;
        if (ImGui.checkbox("Flip H", data.flipHorizontal())) {
            data.setFlipHorizontal(!data.flipHorizontal());
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Flip V", data.flipVertical())) {
            data.setFlipVertical(!data.flipVertical());
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Transpose", data.transpose())) {
            data.setTranspose(!data.transpose());
            changed = true;
        }
        return touchIfChanged(tilemap, changed);
    }

    private boolean renderProbability(SpriteTilemap tilemap, TileData data) {
        probability.set(data.probability());
        ImGui.setNextItemWidth(120.0f);
        if (!ImGui.inputFloat("Probability", probability)) {
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
        ImGui.textDisabled("Terrain bits: click sets the selected terrain, right click clears");
        boolean changed = false;
        for (int row = 0; row < 3; row++) {
            changed |= renderBitRow(tilemap, data, row);
        }
        return touchIfChanged(tilemap, changed);
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
        boolean cleared = ImGui.isItemHovered() && ImGui.isMouseClicked(imgui.flag.ImGuiMouseButton.Right);
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

    private boolean renderCustomData(SpriteTilemap tilemap, TileData data) {
        data.customData().forEach((key, value) -> ImGui.textDisabled(key + " = " + value));
        ImGui.setNextItemWidth(90.0f);
        ImGui.inputText("##customKey", customKey);
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
