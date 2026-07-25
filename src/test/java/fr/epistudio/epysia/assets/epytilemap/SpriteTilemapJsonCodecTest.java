package fr.epistudio.epysia.assets.epytilemap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteTilemapJsonCodecTest {

    private final SpriteTilemapJsonCodec codec = new SpriteTilemapJsonCodec();

    @Test
    void emptyMapRoundTripsByteStable() {
        SpriteTilemap original = new SpriteTilemap(4, 3, 1.0f, 1.0f, "tiles.epyatlas");
        String json = codec.write(original);
        assertFalse(json.contains("x-1"));
        assertFalse(json.contains("solidTiles"));
        SpriteTilemap decoded = codec.read(json);
        assertEquals(json, codec.write(decoded));
        assertEquals(SpriteTilemap.EMPTY_TILE_INDEX, decoded.tileIndex(2, 1));
        assertTrue(decoded.usedBounds().isEmpty());
    }

    @Test
    void fullRowEncodesAsSingleRun() {
        SpriteTilemap original = new SpriteTilemap(5, 2);
        for (int cellX = 0; cellX < 5; cellX++) {
            original.setTile(cellX, 0, 12);
        }
        String json = codec.write(original);
        assertTrue(json.contains("\"5x12\""));
        SpriteTilemap decoded = codec.read(json);
        assertEquals(json, codec.write(decoded));
        assertEquals(12, decoded.tileIndex(4, 0));
        assertEquals(SpriteTilemap.EMPTY_TILE_INDEX, decoded.tileIndex(0, 1));
    }

    @Test
    void singleCellsAndSolidTilesRoundTrip() {
        SpriteTilemap original = new SpriteTilemap(4, 2, 0.5f, 0.25f, "ground.epyatlas");
        original.setTile(1, 0, 7);
        original.setTile(3, 1, 2);
        original.setSolid(7, true);
        original.setSolid(2, true);
        String json = codec.write(original);
        assertTrue(json.contains("\"1x7,2x-1\""));
        assertTrue(json.contains("\"2x-1,1x2\""));
        assertTrue(json.contains("\"origin\""));
        SpriteTilemap decoded = codec.read(json);
        assertEquals(json, codec.write(decoded));
        assertDecodedMatches(decoded);
    }

    private static void assertDecodedMatches(SpriteTilemap decoded) {
        assertEquals(7, decoded.tileIndex(1, 0));
        assertEquals(2, decoded.tileIndex(3, 1));
        assertEquals(0.5f, decoded.cellWidth());
        assertEquals(0.25f, decoded.cellHeight());
        assertEquals("ground.epyatlas", decoded.atlasPath());
        SortedSet<Integer> expectedSolid = new TreeSet<>(List.of(2, 7));
        assertEquals(expectedSolid, decoded.solidTiles());
        assertTrue(decoded.isCellSolid(1, 0));
        assertFalse(decoded.isCellSolid(0, 0));
    }

    @Test
    void rewriteAfterEditsStaysCanonical() {
        SpriteTilemap tilemap = codec.read(codec.write(new SpriteTilemap(3, 1)));
        tilemap.setTile(0, 0, 5);
        tilemap.setTile(1, 0, 5);
        String edited = codec.write(tilemap);
        assertTrue(edited.contains("\"2x5\""));
        assertEquals(edited, codec.write(codec.read(edited)));
    }
}
