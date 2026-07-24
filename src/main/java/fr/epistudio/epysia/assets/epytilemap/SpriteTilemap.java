package fr.epistudio.epysia.assets.epytilemap;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public final class SpriteTilemap {

    public static final int EMPTY_TILE_INDEX = -1;

    private final int width;
    private final int height;
    private final int[] tileIndices;
    private final SortedSet<Integer> solidTiles = new TreeSet<>();
    private float cellWidth;
    private float cellHeight;
    private String atlasPath;
    private long version;

    public SpriteTilemap(int width, int height) {
        this(width, height, 1.0f, 1.0f, "");
    }

    public SpriteTilemap(int width, int height, float cellWidth, float cellHeight, String atlasPath) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.atlasPath = atlasPath;
        this.tileIndices = new int[this.width * this.height];
        Arrays.fill(tileIndices, EMPTY_TILE_INDEX);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float cellWidth() {
        return cellWidth;
    }

    public float cellHeight() {
        return cellHeight;
    }

    public SpriteTilemap setCellSize(float cellWidth, float cellHeight) {
        if (this.cellWidth != cellWidth || this.cellHeight != cellHeight) {
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            version++;
        }
        return this;
    }

    public String atlasPath() {
        return atlasPath;
    }

    public SpriteTilemap setAtlasPath(String value) {
        if (!atlasPath.equals(value)) {
            atlasPath = value;
            version++;
        }
        return this;
    }

    public boolean contains(int cellX, int cellY) {
        return cellX >= 0 && cellX < width && cellY >= 0 && cellY < height;
    }

    public int tileIndex(int cellX, int cellY) {
        if (!contains(cellX, cellY)) {
            return EMPTY_TILE_INDEX;
        }
        return tileIndices[cellY * width + cellX];
    }

    public SpriteTilemap setTile(int cellX, int cellY, int tileIndex) {
        if (contains(cellX, cellY) && tileIndices[cellY * width + cellX] != tileIndex) {
            tileIndices[cellY * width + cellX] = tileIndex;
            version++;
        }
        return this;
    }

    public SpriteTilemap clearTile(int cellX, int cellY) {
        return setTile(cellX, cellY, EMPTY_TILE_INDEX);
    }

    public SortedSet<Integer> solidTiles() {
        return Collections.unmodifiableSortedSet(solidTiles);
    }

    public SpriteTilemap setSolid(int tileIndex, boolean solid) {
        boolean changed = solid ? solidTiles.add(tileIndex) : solidTiles.remove(tileIndex);
        if (changed) {
            version++;
        }
        return this;
    }

    public boolean isSolidTile(int tileIndex) {
        return solidTiles.contains(tileIndex);
    }

    public boolean isCellSolid(int cellX, int cellY) {
        int tileIndex = tileIndex(cellX, cellY);
        return tileIndex != EMPTY_TILE_INDEX && solidTiles.contains(tileIndex);
    }

    public long version() {
        return version;
    }
}
