package fr.epistudio.epysia.assets.epytilemap;

import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

public final class TilemapLayer {

    private final Map<Long, Integer> tileIndices = new HashMap<>();
    private final Vector4f modulate = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private String name;
    private boolean visible = true;
    private boolean collisionEnabled = true;
    private int sortingOrder;
    private CellBounds usedBounds = CellBounds.empty();
    private boolean usedBoundsStale;

    public TilemapLayer(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public TilemapLayer setName(String value) {
        name = value;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public TilemapLayer setVisible(boolean value) {
        visible = value;
        return this;
    }

    public boolean collisionEnabled() {
        return collisionEnabled;
    }

    public TilemapLayer setCollisionEnabled(boolean value) {
        collisionEnabled = value;
        return this;
    }

    public int sortingOrder() {
        return sortingOrder;
    }

    public TilemapLayer setSortingOrder(int value) {
        sortingOrder = value;
        return this;
    }

    public Vector4f modulate() {
        return modulate;
    }

    public TilemapLayer setModulate(float red, float green, float blue, float alpha) {
        modulate.set(red, green, blue, alpha);
        return this;
    }

    public int tileIndex(int cellX, int cellY) {
        return tileIndices.getOrDefault(key(cellX, cellY), SpriteTilemap.EMPTY_TILE_INDEX);
    }

    public boolean setTile(int cellX, int cellY, int tileIndex) {
        long cellKey = key(cellX, cellY);
        if (tileIndex == SpriteTilemap.EMPTY_TILE_INDEX) {
            return clearTile(cellKey);
        }
        Integer previous = tileIndices.put(cellKey, tileIndex);
        if (previous != null && previous == tileIndex) {
            return false;
        }
        usedBounds = usedBounds.including(cellX, cellY);
        return true;
    }

    private boolean clearTile(long cellKey) {
        if (tileIndices.remove(cellKey) == null) {
            return false;
        }
        usedBoundsStale = true;
        return true;
    }

    public int paintedCellCount() {
        return tileIndices.size();
    }

    public CellBounds usedBounds() {
        if (usedBoundsStale) {
            usedBounds = recomputeBounds();
            usedBoundsStale = false;
        }
        return usedBounds;
    }

    private CellBounds recomputeBounds() {
        CellBounds bounds = CellBounds.empty();
        for (Long cellKey : tileIndices.keySet()) {
            bounds = bounds.including(unpackX(cellKey), unpackY(cellKey));
        }
        return bounds;
    }

    private static long key(int cellX, int cellY) {
        return (((long) cellY) << 32) | (cellX & 0xFFFFFFFFL);
    }

    private static int unpackX(long cellKey) {
        return (int) cellKey;
    }

    private static int unpackY(long cellKey) {
        return (int) (cellKey >> 32);
    }
}
