package fr.epistudio.epysia.assets.epytilemap;

import java.util.ArrayList;
import java.util.List;

public record TerrainsPattern(int terrain, List<Integer> peeringTerrains) {

    public TerrainsPattern {
        peeringTerrains = List.copyOf(peeringTerrains);
    }

    public static TerrainsPattern empty() {
        List<Integer> peering = new ArrayList<>();
        for (int index = 0; index < TileNeighbor.values().length; index++) {
            peering.add(TileData.NO_TERRAIN);
        }
        return new TerrainsPattern(TileData.NO_TERRAIN, peering);
    }

    public static TerrainsPattern of(TileData data, TerrainMatchMode mode) {
        List<Integer> peering = new ArrayList<>();
        for (TileNeighbor neighbor : TileNeighbor.values()) {
            peering.add(neighbor.matches(mode) ? data.peeringTerrain(neighbor) : TileData.NO_TERRAIN);
        }
        return new TerrainsPattern(data.terrain(), peering);
    }

    public int peeringTerrain(TileNeighbor neighbor) {
        return peeringTerrains.get(neighbor.ordinal());
    }
}
