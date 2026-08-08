package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.worldgen.ChunkCoordinate;
import fr.epistudio.epysia.worldgen.LayerGrid;
import fr.epistudio.epysia.worldgen.WorldRect;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NetworkInterestGrid {
    private final LayerGrid grid;
    private final Map<ChunkCoordinate, List<Integer>> objectsByCell = new LinkedHashMap<>();
    private final Map<Integer, Vector3f> positionByNetworkId = new LinkedHashMap<>();

    public NetworkInterestGrid(float cellSize) {
        this.grid = new LayerGrid(Math.max(1.0f, cellSize));
    }

    public void rebuild(Map<Integer, Vector3f> positions) {
        objectsByCell.clear();
        positionByNetworkId.clear();
        positionByNetworkId.putAll(positions);
        for (Map.Entry<Integer, Vector3f> entry : positions.entrySet()) {
            Vector3f position = entry.getValue();
            objectsByCell.computeIfAbsent(grid.coordinateAt(position.x, position.z),
                    ignored -> new ArrayList<>()).add(entry.getKey());
        }
    }

    public Set<Integer> within(Vector3f centre, float radius, Set<Integer> alwaysRelevant) {
        Set<Integer> relevant = new LinkedHashSet<>(alwaysRelevant);
        float radiusSquared = radius * radius;
        for (ChunkCoordinate cell : grid.covering(WorldRect.around(centre.x, centre.z, radius))) {
            collectFrom(cell, centre, radiusSquared, relevant);
        }
        return relevant;
    }

    private void collectFrom(ChunkCoordinate cell, Vector3f centre, float radiusSquared,
                             Set<Integer> relevant) {
        List<Integer> candidates = objectsByCell.get(cell);
        if (candidates == null) {
            return;
        }
        for (int networkId : candidates) {
            Vector3f position = positionByNetworkId.get(networkId);
            if (position != null && centre.distanceSquared(position) <= radiusSquared) {
                relevant.add(networkId);
            }
        }
    }

    public Set<Integer> positionedNetworkIds() {
        return positionByNetworkId.keySet();
    }

    public int cellCount() {
        return objectsByCell.size();
    }

    public void clear() {
        objectsByCell.clear();
        positionByNetworkId.clear();
    }
}
