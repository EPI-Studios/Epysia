package fr.epistudio.epysia.editor.tilemap;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;

import java.util.ArrayList;
import java.util.List;

public final class TileBrush {

    public record ClipboardCell(int offsetX, int offsetY, int tileIndex) {
    }

    private final List<ClipboardCell> clipboard = new ArrayList<>();
    private TileTool tool = TileTool.PAINT;
    private int tileIndex;
    private int layerIndex;
    private int terrainIndex;

    public TileTool tool() {
        return tool;
    }

    public TileBrush setTool(TileTool value) {
        tool = value;
        return this;
    }

    public int tileIndex() {
        return tileIndex;
    }

    public TileBrush setTileIndex(int value) {
        tileIndex = value;
        return this;
    }

    public int layerIndex() {
        return layerIndex;
    }

    public TileBrush setLayerIndex(int value) {
        layerIndex = Math.max(0, value);
        return this;
    }

    public int terrainIndex() {
        return terrainIndex;
    }

    public TileBrush setTerrainIndex(int value) {
        terrainIndex = value;
        return this;
    }

    public int paintValue() {
        return tool == TileTool.ERASE ? SpriteTilemap.EMPTY_TILE_INDEX : tileIndex;
    }

    public List<ClipboardCell> clipboard() {
        return List.copyOf(clipboard);
    }

    public boolean clipboardFilled() {
        return !clipboard.isEmpty();
    }

    public TileBrush replaceClipboard(List<ClipboardCell> cells) {
        clipboard.clear();
        clipboard.addAll(cells);
        return this;
    }
}
