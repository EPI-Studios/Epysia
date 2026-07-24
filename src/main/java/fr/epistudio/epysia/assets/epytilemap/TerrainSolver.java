package fr.epistudio.epysia.assets.epytilemap;

import fr.epistudio.epysia.assets.epytilemap.TerrainConstraintKey.CellPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TerrainSolver {

    private static final int PAINTED_PRIORITY = 10;
    private static final int RESOLVED_PRIORITY = 5;

    private record ConstraintValue(int terrain, int priority) {
    }

    private final SpriteTilemap tilemap;
    private final int layerIndex;
    private final Map<TerrainsPattern, Integer> tileByPattern = new LinkedHashMap<>();

    public TerrainSolver(SpriteTilemap tilemap, int layerIndex) {
        this.tilemap = tilemap;
        this.layerIndex = layerIndex;
        for (Map.Entry<Integer, TileData> entry : tilemap.tileDataByIndex().entrySet()) {
            if (entry.getValue().participatesInTerrain()) {
                tileByPattern.putIfAbsent(patternOf(entry.getValue()), entry.getKey());
            }
        }
    }

    public boolean usable() {
        return !tileByPattern.isEmpty();
    }

    public Map<CellPosition, Integer> fillConnect(List<CellPosition> painted, int terrain) {
        Set<CellPosition> paintedSet = new LinkedHashSet<>(painted);
        List<CellPosition> canModify = expandWithNeighbors(painted);
        Set<CellPosition> connected = cellsCarryingTerrain(canModify, paintedSet, terrain);
        Map<TerrainConstraintKey, ConstraintValue> constraints = new HashMap<>();
        for (CellPosition cell : painted) {
            constraints.put(TerrainConstraintKey.center(cell.cellX(), cell.cellY()),
                    new ConstraintValue(terrain, PAINTED_PRIORITY));
            addPaintedPeeringConstraints(constraints, cell, terrain, connected);
        }
        constraints.putAll(constraintsFromExistingTiles(paintedSet));
        return solve(canModify, constraints);
    }

    private List<CellPosition> expandWithNeighbors(List<CellPosition> painted) {
        Set<CellPosition> ordered = new LinkedHashSet<>(painted);
        for (CellPosition cell : painted) {
            for (TileNeighbor neighbor : TileNeighbor.values()) {
                ordered.add(new CellPosition(cell.cellX() + neighbor.cellOffsetX(),
                        cell.cellY() + neighbor.cellOffsetY()));
            }
        }
        return new ArrayList<>(ordered);
    }

    private Set<CellPosition> cellsCarryingTerrain(List<CellPosition> candidates,
                                                   Set<CellPosition> painted, int terrain) {
        Set<CellPosition> connected = new LinkedHashSet<>(painted);
        for (CellPosition cell : candidates) {
            if (existingData(cell).map(data -> data.terrain() == terrain).orElse(false)) {
                connected.add(cell);
            }
        }
        return connected;
    }

    private void addPaintedPeeringConstraints(Map<TerrainConstraintKey, ConstraintValue> constraints,
                                              CellPosition cell, int terrain, Set<CellPosition> connected) {
        for (TileNeighbor neighbor : validNeighbors()) {
            TerrainConstraintKey key = TerrainConstraintKey.peering(cell.cellX(), cell.cellY(), neighbor);
            if (connected.containsAll(key.overlappingBits().keySet())) {
                constraints.put(key, new ConstraintValue(terrain, PAINTED_PRIORITY));
            }
        }
    }

    private Map<TerrainConstraintKey, ConstraintValue> constraintsFromExistingTiles(Set<CellPosition> painted) {
        Map<TerrainConstraintKey, ConstraintValue> constraints = new HashMap<>();
        for (TerrainConstraintKey key : boundaryKeys(painted)) {
            majorityTerrain(key).ifPresent(terrain ->
                    constraints.put(key, new ConstraintValue(terrain, PAINTED_PRIORITY)));
        }
        return constraints;
    }

    private Set<TerrainConstraintKey> boundaryKeys(Set<CellPosition> painted) {
        Set<TerrainConstraintKey> keys = new LinkedHashSet<>();
        for (CellPosition cell : painted) {
            for (TileNeighbor neighbor : validNeighbors()) {
                keys.add(TerrainConstraintKey.peering(cell.cellX(), cell.cellY(), neighbor));
            }
        }
        return keys;
    }

    private Optional<Integer> majorityTerrain(TerrainConstraintKey key) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<CellPosition, TileNeighbor> entry : key.overlappingBits().entrySet()) {
            existingData(entry.getKey())
                    .map(data -> data.peeringTerrain(entry.getValue()))
                    .filter(terrain -> terrain != TileData.NO_TERRAIN)
                    .ifPresent(terrain -> counts.merge(terrain, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    private Map<CellPosition, Integer> solve(List<CellPosition> canModify,
                                             Map<TerrainConstraintKey, ConstraintValue> constraints) {
        Map<CellPosition, Integer> output = new LinkedHashMap<>();
        for (CellPosition cell : canModify) {
            TerrainsPattern current = existingData(cell).map(this::patternOf).orElseGet(TerrainsPattern::empty);
            TerrainsPattern chosen = bestPattern(cell, constraints, current);
            Integer tileIndex = tileByPattern.get(chosen);
            if (tileIndex != null) {
                output.put(cell, tileIndex);
                recordResolvedConstraints(constraints, cell, chosen);
            }
        }
        return output;
    }

    private void recordResolvedConstraints(Map<TerrainConstraintKey, ConstraintValue> constraints,
                                           CellPosition cell, TerrainsPattern pattern) {
        constraints.put(TerrainConstraintKey.center(cell.cellX(), cell.cellY()),
                new ConstraintValue(pattern.terrain(), RESOLVED_PRIORITY));
        for (TileNeighbor neighbor : validNeighbors()) {
            constraints.put(TerrainConstraintKey.peering(cell.cellX(), cell.cellY(), neighbor),
                    new ConstraintValue(pattern.peeringTerrain(neighbor), RESOLVED_PRIORITY));
        }
    }

    private TerrainsPattern bestPattern(CellPosition cell, Map<TerrainConstraintKey, ConstraintValue> constraints,
                                        TerrainsPattern current) {
        TerrainsPattern best = current;
        int bestScore = Integer.MAX_VALUE;
        for (TerrainsPattern candidate : tileByPattern.keySet()) {
            int score = scoreOf(cell, candidate, constraints, current);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int scoreOf(CellPosition cell, TerrainsPattern candidate,
                        Map<TerrainConstraintKey, ConstraintValue> constraints, TerrainsPattern current) {
        int score = mismatchScore(TerrainConstraintKey.center(cell.cellX(), cell.cellY()),
                candidate.terrain(), current.terrain(), constraints);
        if (score < 0) {
            return -1;
        }
        for (TileNeighbor neighbor : validNeighbors()) {
            int bitScore = mismatchScore(TerrainConstraintKey.peering(cell.cellX(), cell.cellY(), neighbor),
                    candidate.peeringTerrain(neighbor), current.peeringTerrain(neighbor), constraints);
            if (bitScore < 0) {
                return -1;
            }
            score += bitScore;
        }
        return score;
    }

    private int mismatchScore(TerrainConstraintKey key, int candidateTerrain, int currentTerrain,
                              Map<TerrainConstraintKey, ConstraintValue> constraints) {
        ConstraintValue constraint = constraints.get(key);
        if (constraint == null) {
            return currentTerrain == candidateTerrain ? 0 : -1;
        }
        return constraint.terrain() == candidateTerrain ? 0 : constraint.priority();
    }

    private List<TileNeighbor> validNeighbors() {
        List<TileNeighbor> neighbors = new ArrayList<>();
        for (TileNeighbor neighbor : TileNeighbor.values()) {
            if (neighbor.matches(tilemap.terrainMatchMode())) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    private TerrainsPattern patternOf(TileData data) {
        return TerrainsPattern.of(data, tilemap.terrainMatchMode());
    }

    private Optional<TileData> existingData(CellPosition cell) {
        int tileIndex = tilemap.tileIndex(layerIndex, cell.cellX(), cell.cellY());
        if (tileIndex == SpriteTilemap.EMPTY_TILE_INDEX) {
            return Optional.empty();
        }
        return tilemap.existingTileData(tileIndex).filter(TileData::participatesInTerrain);
    }
}
