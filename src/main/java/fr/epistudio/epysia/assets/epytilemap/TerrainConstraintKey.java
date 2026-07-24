package fr.epistudio.epysia.assets.epytilemap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record TerrainConstraintKey(int cellX, int cellY, Optional<TileNeighbor> bit) {

    public static TerrainConstraintKey center(int cellX, int cellY) {
        return new TerrainConstraintKey(cellX, cellY, Optional.empty());
    }

    public static TerrainConstraintKey peering(int cellX, int cellY, TileNeighbor bit) {
        return switch (bit) {
            case RIGHT, TOP, TOP_RIGHT -> new TerrainConstraintKey(cellX, cellY, Optional.of(bit));
            case LEFT -> new TerrainConstraintKey(cellX - 1, cellY, Optional.of(TileNeighbor.RIGHT));
            case BOTTOM -> new TerrainConstraintKey(cellX, cellY - 1, Optional.of(TileNeighbor.TOP));
            case TOP_LEFT -> new TerrainConstraintKey(cellX - 1, cellY, Optional.of(TileNeighbor.TOP_RIGHT));
            case BOTTOM_RIGHT -> new TerrainConstraintKey(cellX, cellY - 1, Optional.of(TileNeighbor.TOP_RIGHT));
            case BOTTOM_LEFT -> new TerrainConstraintKey(cellX - 1, cellY - 1, Optional.of(TileNeighbor.TOP_RIGHT));
        };
    }

    public Map<CellPosition, TileNeighbor> overlappingBits() {
        Map<CellPosition, TileNeighbor> overlapping = new LinkedHashMap<>();
        if (bit.isEmpty()) {
            return overlapping;
        }
        return switch (bit.get()) {
            case RIGHT -> sideOverlap(overlapping, TileNeighbor.RIGHT, TileNeighbor.LEFT, 1, 0);
            case TOP -> sideOverlap(overlapping, TileNeighbor.TOP, TileNeighbor.BOTTOM, 0, 1);
            default -> cornerOverlap(overlapping);
        };
    }

    private Map<CellPosition, TileNeighbor> sideOverlap(Map<CellPosition, TileNeighbor> overlapping,
                                                        TileNeighbor own, TileNeighbor other,
                                                        int offsetX, int offsetY) {
        overlapping.put(new CellPosition(cellX, cellY), own);
        overlapping.put(new CellPosition(cellX + offsetX, cellY + offsetY), other);
        return overlapping;
    }

    private Map<CellPosition, TileNeighbor> cornerOverlap(Map<CellPosition, TileNeighbor> overlapping) {
        overlapping.put(new CellPosition(cellX, cellY), TileNeighbor.TOP_RIGHT);
        overlapping.put(new CellPosition(cellX + 1, cellY), TileNeighbor.TOP_LEFT);
        overlapping.put(new CellPosition(cellX, cellY + 1), TileNeighbor.BOTTOM_RIGHT);
        overlapping.put(new CellPosition(cellX + 1, cellY + 1), TileNeighbor.BOTTOM_LEFT);
        return overlapping;
    }

    public record CellPosition(int cellX, int cellY) {
    }
}
