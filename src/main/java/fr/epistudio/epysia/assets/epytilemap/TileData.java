package fr.epistudio.epysia.assets.epytilemap;

import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TileData {

    public static final int NO_TERRAIN = -1;

    private final List<TileCollisionShape> collisionShapes = new ArrayList<>();
    private final int[] peeringTerrains = new int[TileNeighbor.values().length];
    private final Map<String, String> customData = new LinkedHashMap<>();
    private final Vector4f modulate = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private boolean flipHorizontal;
    private boolean flipVertical;
    private boolean transpose;
    private float probability = 1.0f;
    private int terrain = NO_TERRAIN;
    private int zIndex;

    public TileData() {
        Arrays.fill(peeringTerrains, NO_TERRAIN);
    }

    public List<TileCollisionShape> collisionShapes() {
        return Collections.unmodifiableList(collisionShapes);
    }

    public TileData addCollisionShape(TileCollisionShape shape) {
        if (shape.valid()) {
            collisionShapes.add(shape);
        }
        return this;
    }

    public TileData removeCollisionShape(int shapeIndex) {
        if (shapeIndex >= 0 && shapeIndex < collisionShapes.size()) {
            collisionShapes.remove(shapeIndex);
        }
        return this;
    }

    public TileData replaceCollisionShape(int shapeIndex, TileCollisionShape shape) {
        if (shapeIndex >= 0 && shapeIndex < collisionShapes.size() && shape.valid()) {
            collisionShapes.set(shapeIndex, shape);
        }
        return this;
    }

    public TileData clearCollisionShapes() {
        collisionShapes.clear();
        return this;
    }

    public boolean flipHorizontal() {
        return flipHorizontal;
    }

    public TileData setFlipHorizontal(boolean value) {
        flipHorizontal = value;
        return this;
    }

    public boolean flipVertical() {
        return flipVertical;
    }

    public TileData setFlipVertical(boolean value) {
        flipVertical = value;
        return this;
    }

    public boolean transpose() {
        return transpose;
    }

    public TileData setTranspose(boolean value) {
        transpose = value;
        return this;
    }

    public float probability() {
        return probability;
    }

    public TileData setProbability(float value) {
        probability = Math.max(0.0f, value);
        return this;
    }

    public int terrain() {
        return terrain;
    }

    public TileData setTerrain(int value) {
        terrain = value;
        return this;
    }

    public int peeringTerrain(TileNeighbor neighbor) {
        return peeringTerrains[neighbor.ordinal()];
    }

    public TileData setPeeringTerrain(TileNeighbor neighbor, int value) {
        peeringTerrains[neighbor.ordinal()] = value;
        return this;
    }

    public int zIndex() {
        return zIndex;
    }

    public TileData setZIndex(int value) {
        zIndex = value;
        return this;
    }

    public Vector4f modulate() {
        return modulate;
    }

    public TileData setModulate(float red, float green, float blue, float alpha) {
        modulate.set(red, green, blue, alpha);
        return this;
    }

    public Map<String, String> customData() {
        return Collections.unmodifiableMap(customData);
    }

    public Optional<String> customValue(String key) {
        return Optional.ofNullable(customData.get(key));
    }

    public TileData setCustomValue(String key, String value) {
        customData.put(key, value);
        return this;
    }

    public TileData removeCustomValue(String key) {
        customData.remove(key);
        return this;
    }

    public boolean participatesInTerrain() {
        if (terrain != NO_TERRAIN) {
            return true;
        }
        for (int peering : peeringTerrains) {
            if (peering != NO_TERRAIN) {
                return true;
            }
        }
        return false;
    }

    public boolean defaultValued() {
        return collisionShapes.isEmpty() && customData.isEmpty() && !participatesInTerrain()
                && !flipHorizontal && !flipVertical && !transpose
                && probability == 1.0f && zIndex == 0
                && modulate.equals(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public TileData copy() {
        TileData clone = new TileData();
        collisionShapes.forEach(clone::addCollisionShape);
        System.arraycopy(peeringTerrains, 0, clone.peeringTerrains, 0, peeringTerrains.length);
        customData.forEach(clone::setCustomValue);
        clone.modulate.set(modulate);
        return clone.setFlipHorizontal(flipHorizontal).setFlipVertical(flipVertical)
                .setTranspose(transpose).setProbability(probability)
                .setTerrain(terrain).setZIndex(zIndex);
    }
}
