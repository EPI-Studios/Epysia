package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.List;

public record LayerGrid(float chunkSize) {

    public LayerGrid {
        if (!(chunkSize > 0.0f)) {
            throw new EpysiaException("Layer chunk size must be positive, got " + chunkSize);
        }
    }

    public int chunkOf(float worldCoordinate) {
        return (int) Math.floor(worldCoordinate / chunkSize);
    }

    public ChunkCoordinate coordinateAt(float worldX, float worldZ) {
        return new ChunkCoordinate(chunkOf(worldX), chunkOf(worldZ));
    }

    public WorldRect boundsOf(ChunkCoordinate coordinate) {
        float originX = coordinate.x() * chunkSize;
        float originZ = coordinate.z() * chunkSize;
        return new WorldRect(originX, originZ, originX + chunkSize, originZ + chunkSize);
    }

    public List<ChunkCoordinate> covering(WorldRect rect) {
        int minimumX = chunkOf(rect.minX());
        int minimumZ = chunkOf(rect.minZ());
        int maximumX = chunkOf(rect.maxX());
        int maximumZ = chunkOf(rect.maxZ());
        List<ChunkCoordinate> coordinates = new ArrayList<>();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                coordinates.add(new ChunkCoordinate(x, z));
            }
        }
        return coordinates;
    }
}
