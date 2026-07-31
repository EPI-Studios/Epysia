package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LayerContext {

    private final WorldRect bounds;
    private final Map<GenerationLayer<?>, Map<ChunkCoordinate, Object>> visible;
    private final Map<GenerationLayer<?>, LayerGrid> grids;

    LayerContext(WorldRect bounds, Map<GenerationLayer<?>, Map<ChunkCoordinate, Object>> visible,
                 Map<GenerationLayer<?>, LayerGrid> grids) {
        this.bounds = bounds;
        this.visible = visible;
        this.grids = grids;
    }

    public WorldRect bounds() {
        return bounds;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> chunks(GenerationLayer<T> layer) {
        return (List<T>) new ArrayList<>(chunksOf(layer).values());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> chunkAt(GenerationLayer<T> layer, float worldX, float worldZ) {
        LayerGrid grid = gridOf(layer);
        return Optional.ofNullable((T) chunksOf(layer).get(grid.coordinateAt(worldX, worldZ)));
    }

    @SuppressWarnings("unchecked")
    public <T> T chunk(GenerationLayer<T> layer, ChunkCoordinate coordinate) {
        Object data = chunksOf(layer).get(coordinate);
        if (data == null) {
            throw new EpysiaException("Layer " + layer.name() + " chunk " + coordinate
                    + " is outside the declared context of this generation");
        }
        return (T) data;
    }

    public <T> List<ChunkCoordinate> coordinates(GenerationLayer<T> layer) {
        return List.copyOf(chunksOf(layer).keySet());
    }

    private Map<ChunkCoordinate, Object> chunksOf(GenerationLayer<?> layer) {
        Map<ChunkCoordinate, Object> chunks = visible.get(layer);
        if (chunks == null) {
            throw new EpysiaException("Layer " + layer.name()
                    + " was not declared as a dependency, so its chunks are not readable here");
        }
        return chunks;
    }

    private LayerGrid gridOf(GenerationLayer<?> layer) {
        LayerGrid grid = grids.get(layer);
        if (grid == null) {
            throw new EpysiaException("Layer " + layer.name() + " is not registered in this world");
        }
        return grid;
    }
}
