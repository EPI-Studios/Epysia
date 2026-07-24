package fr.epistudio.epysia.editor.tilemap;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TerrainConstraintKey.CellPosition;
import fr.epistudio.epysia.assets.epytilemap.TerrainSolver;
import fr.epistudio.epysia.editor.command.builtin.TilemapPaintCommand;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class TilePaintController {

    public record CellRange(int minX, int minY, int maxX, int maxY) {
    }

    private final TileBrush brush;
    private final Consumer<TilemapPaintCommand> history;
    private final Map<Long, int[]> strokeCells = new LinkedHashMap<>();
    private final Set<CellPosition> terrainCells = new LinkedHashSet<>();
    private SpriteTilemap strokeTilemap;
    private boolean strokeActive;
    private boolean erasing;
    private int anchorCellX;
    private int anchorCellY;
    private CellRange selection = new CellRange(0, 0, -1, -1);

    public TilePaintController(TileBrush brush, Consumer<TilemapPaintCommand> history) {
        this.brush = brush;
        this.history = history;
    }

    public void update(SpriteTilemap tilemap, int cellX, int cellY, boolean hovered) {
        if (strokeActive) {
            advance(cellX, cellY);
            return;
        }
        if (!hovered) {
            return;
        }
        handleToolShortcuts();
        handleClipboardKeys(tilemap, cellX, cellY);
        begin(tilemap, cellX, cellY);
    }

    public void cancel() {
        strokeActive = false;
        strokeCells.clear();
        terrainCells.clear();
        strokeTilemap = null;
    }

    public boolean active() {
        return strokeActive;
    }

    public Optional<CellRange> selectionRange() {
        return selection.maxX() < selection.minX() ? Optional.empty() : Optional.of(selection);
    }

    public Optional<CellRange> pendingRange(int cellX, int cellY) {
        if (!strokeActive || !brush.tool().rectangular()) {
            return Optional.empty();
        }
        return Optional.of(rangeBetween(anchorCellX, anchorCellY, cellX, cellY));
    }

    private void begin(SpriteTilemap tilemap, int cellX, int cellY) {
        boolean rightPressed = ImGui.isMouseClicked(ImGuiMouseButton.Right);
        if (!rightPressed && !ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        strokeActive = true;
        erasing = rightPressed;
        strokeTilemap = tilemap;
        anchorCellX = cellX;
        anchorCellY = cellY;
        strokeCells.clear();
        terrainCells.clear();
        applyImmediateTools(tilemap, cellX, cellY);
    }

    private void applyImmediateTools(SpriteTilemap tilemap, int cellX, int cellY) {
        switch (brush.tool()) {
            case PICK -> pickTile(tilemap, cellX, cellY);
            case BUCKET -> bucketFill(tilemap, cellX, cellY);
            case PAINT, ERASE -> recordEdit(cellX, cellY, paintValue());
            case TERRAIN -> recordTerrainCell(cellX, cellY);
            default -> { }
        }
    }

    private void advance(int cellX, int cellY) {
        int button = erasing ? ImGuiMouseButton.Right : ImGuiMouseButton.Left;
        if (ImGui.isMouseDown(button)) {
            continueDrag(cellX, cellY);
            return;
        }
        finish(cellX, cellY);
    }

    private void continueDrag(int cellX, int cellY) {
        if (brush.tool().draggable() || erasing) {
            recordEdit(cellX, cellY, paintValue());
        } else if (brush.tool() == TileTool.TERRAIN) {
            recordTerrainCell(cellX, cellY);
        }
    }

    private void finish(int cellX, int cellY) {
        switch (brush.tool()) {
            case LINE -> applyLine(cellX, cellY);
            case RECTANGLE -> applyRectangle(rangeBetween(anchorCellX, anchorCellY, cellX, cellY));
            case SELECT -> selection = rangeBetween(anchorCellX, anchorCellY, cellX, cellY);
            case TERRAIN -> applyTerrain();
            default -> { }
        }
        commit();
    }

    private void applyLine(int endCellX, int endCellY) {
        int steps = Math.max(Math.abs(endCellX - anchorCellX), Math.abs(endCellY - anchorCellY));
        for (int step = 0; step <= steps; step++) {
            float ratio = steps == 0 ? 0.0f : (float) step / steps;
            recordEdit(Math.round(anchorCellX + (endCellX - anchorCellX) * ratio),
                    Math.round(anchorCellY + (endCellY - anchorCellY) * ratio), paintValue());
        }
    }

    private void applyRectangle(CellRange range) {
        for (int cellY = range.minY(); cellY <= range.maxY(); cellY++) {
            for (int cellX = range.minX(); cellX <= range.maxX(); cellX++) {
                recordEdit(cellX, cellY, paintValue());
            }
        }
    }

    private void bucketFill(SpriteTilemap tilemap, int cellX, int cellY) {
        int target = tilemap.tileIndex(brush.layerIndex(), cellX, cellY);
        int replacement = paintValue();
        if (target == replacement || !tilemap.contains(cellX, cellY)) {
            return;
        }
        Deque<int[]> pending = new ArrayDeque<>();
        Set<Long> visited = new LinkedHashSet<>();
        pending.add(new int[]{cellX, cellY});
        while (!pending.isEmpty() && visited.size() < tilemap.width() * tilemap.height()) {
            expandFlood(tilemap, pending, visited, pending.poll(), target, replacement);
        }
    }

    private void expandFlood(SpriteTilemap tilemap, Deque<int[]> pending, Set<Long> visited,
                             int[] cell, int target, int replacement) {
        if (!tilemap.contains(cell[0], cell[1]) || !visited.add(key(cell[0], cell[1]))
                || tilemap.tileIndex(brush.layerIndex(), cell[0], cell[1]) != target) {
            return;
        }
        recordEdit(cell[0], cell[1], replacement);
        pending.add(new int[]{cell[0] + 1, cell[1]});
        pending.add(new int[]{cell[0] - 1, cell[1]});
        pending.add(new int[]{cell[0], cell[1] + 1});
        pending.add(new int[]{cell[0], cell[1] - 1});
    }

    private void pickTile(SpriteTilemap tilemap, int cellX, int cellY) {
        int tileIndex = tilemap.tileIndex(brush.layerIndex(), cellX, cellY);
        if (tileIndex != SpriteTilemap.EMPTY_TILE_INDEX) {
            brush.setTileIndex(tileIndex).setTool(TileTool.PAINT);
        }
    }

    private void recordTerrainCell(int cellX, int cellY) {
        if (strokeTilemap != null && strokeTilemap.contains(cellX, cellY)) {
            terrainCells.add(new CellPosition(cellX, cellY));
        }
    }

    private void applyTerrain() {
        if (strokeTilemap == null || terrainCells.isEmpty()) {
            return;
        }
        TerrainSolver solver = new TerrainSolver(strokeTilemap, brush.layerIndex());
        if (!solver.usable()) {
            return;
        }
        solver.fillConnect(new ArrayList<>(terrainCells), brush.terrainIndex())
                .forEach((cell, tileIndex) -> recordEdit(cell.cellX(), cell.cellY(), tileIndex));
    }

    private void handleToolShortcuts() {
        if (ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl()) {
            return;
        }
        for (TileTool tool : TileTool.values()) {
            if (ImGui.isKeyPressed(shortcutKeyOf(tool))) {
                brush.setTool(tool);
                return;
            }
        }
    }

    private static int shortcutKeyOf(TileTool tool) {
        return switch (tool) {
            case PAINT -> ImGuiKey.B;
            case ERASE -> ImGuiKey.E;
            case LINE -> ImGuiKey.L;
            case RECTANGLE -> ImGuiKey.R;
            case BUCKET -> ImGuiKey.G;
            case PICK -> ImGuiKey.I;
            case SELECT -> ImGuiKey.S;
            case TERRAIN -> ImGuiKey.T;
        };
    }

    private void handleClipboardKeys(SpriteTilemap tilemap, int cellX, int cellY) {
        if (ImGui.getIO().getWantTextInput()) {
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.C)) {
            selectionRange().ifPresent(range -> copySelection(tilemap, range));
        } else if (ImGui.isKeyPressed(ImGuiKey.V) && brush.clipboardFilled()) {
            pasteAt(tilemap, cellX, cellY);
        } else if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
            selectionRange().ifPresent(range -> eraseSelection(tilemap, range));
        }
    }

    private void copySelection(SpriteTilemap tilemap, CellRange range) {
        List<TileBrush.ClipboardCell> cells = new ArrayList<>();
        for (int cellY = range.minY(); cellY <= range.maxY(); cellY++) {
            for (int cellX = range.minX(); cellX <= range.maxX(); cellX++) {
                cells.add(new TileBrush.ClipboardCell(cellX - range.minX(), cellY - range.minY(),
                        tilemap.tileIndex(brush.layerIndex(), cellX, cellY)));
            }
        }
        brush.replaceClipboard(cells);
    }

    private void pasteAt(SpriteTilemap tilemap, int cellX, int cellY) {
        strokeTilemap = tilemap;
        for (TileBrush.ClipboardCell cell : brush.clipboard()) {
            recordEdit(cellX + cell.offsetX(), cellY + cell.offsetY(), cell.tileIndex());
        }
        commit();
    }

    private void eraseSelection(SpriteTilemap tilemap, CellRange range) {
        strokeTilemap = tilemap;
        for (int cellY = range.minY(); cellY <= range.maxY(); cellY++) {
            for (int cellX = range.minX(); cellX <= range.maxX(); cellX++) {
                recordEdit(cellX, cellY, SpriteTilemap.EMPTY_TILE_INDEX);
            }
        }
        commit();
    }

    private int paintValue() {
        return erasing ? SpriteTilemap.EMPTY_TILE_INDEX : brush.paintValue();
    }

    private void recordEdit(int cellX, int cellY, int after) {
        if (strokeTilemap == null || !strokeTilemap.contains(cellX, cellY)) {
            return;
        }
        int layerIndex = brush.layerIndex();
        int[] existing = strokeCells.get(key(cellX, cellY));
        if (existing == null) {
            strokeCells.put(key(cellX, cellY), new int[]{cellX, cellY,
                    strokeTilemap.tileIndex(layerIndex, cellX, cellY), after});
        } else {
            existing[3] = after;
        }
        strokeTilemap.setTile(layerIndex, cellX, cellY, after);
    }

    private void commit() {
        List<TilemapPaintCommand.TileEdit> edits = new ArrayList<>();
        for (int[] cell : strokeCells.values()) {
            if (cell[2] != cell[3]) {
                edits.add(new TilemapPaintCommand.TileEdit(brush.layerIndex(), cell[0], cell[1], cell[2], cell[3]));
            }
        }
        SpriteTilemap tilemap = strokeTilemap;
        String label = brush.tool().historyLabel();
        cancel();
        if (!edits.isEmpty() && tilemap != null) {
            history.accept(new TilemapPaintCommand(tilemap, edits, label));
        }
    }

    private static CellRange rangeBetween(int firstX, int firstY, int secondX, int secondY) {
        return new CellRange(Math.min(firstX, secondX), Math.min(firstY, secondY),
                Math.max(firstX, secondX), Math.max(firstY, secondY));
    }

    private static long key(int cellX, int cellY) {
        return (((long) cellY) << 32) | (cellX & 0xFFFFFFFFL);
    }
}
