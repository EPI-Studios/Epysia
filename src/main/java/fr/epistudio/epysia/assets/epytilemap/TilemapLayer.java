package fr.epistudio.epysia.assets.epytilemap;

import org.joml.Vector4f;

import java.util.Arrays;

public final class TilemapLayer {

    private final int width;
    private final int height;
    private final int[] tileIndices;
    private final Vector4f modulate = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private String name;
    private boolean visible = true;
    private boolean collisionEnabled = true;
    private int sortingOrder;

    public TilemapLayer(String name, int width, int height) {
        this.name = name;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.tileIndices = new int[this.width * this.height];
        Arrays.fill(tileIndices, SpriteTilemap.EMPTY_TILE_INDEX);
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

    public boolean contains(int cellX, int cellY) {
        return cellX >= 0 && cellX < width && cellY >= 0 && cellY < height;
    }

    public int tileIndex(int cellX, int cellY) {
        if (!contains(cellX, cellY)) {
            return SpriteTilemap.EMPTY_TILE_INDEX;
        }
        return tileIndices[cellY * width + cellX];
    }

    public boolean setTile(int cellX, int cellY, int tileIndex) {
        if (!contains(cellX, cellY) || tileIndices[cellY * width + cellX] == tileIndex) {
            return false;
        }
        tileIndices[cellY * width + cellX] = tileIndex;
        return true;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
