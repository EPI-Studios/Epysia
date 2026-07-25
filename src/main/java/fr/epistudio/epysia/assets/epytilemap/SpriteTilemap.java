package fr.epistudio.epysia.assets.epytilemap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

public final class SpriteTilemap {

    public static final int EMPTY_TILE_INDEX = -1;

    private final int width;
    private final int height;
    private final List<TilemapLayer> layers = new ArrayList<>();
    private final SortedSet<Integer> solidTiles = new TreeSet<>();
    private final Map<Integer, TileData> tileData = new LinkedHashMap<>();
    private final Map<Integer, String> scenePaths = new LinkedHashMap<>();
    private final List<TerrainDefinition> terrains = new ArrayList<>();
    private TerrainMatchMode terrainMatchMode = TerrainMatchMode.CORNERS_AND_SIDES;
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
        layers.add(new TilemapLayer("Layer 1"));
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

    public List<TilemapLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    public int layerCount() {
        return layers.size();
    }

    public TilemapLayer layer(int layerIndex) {
        return layers.get(Math.clamp(layerIndex, 0, layers.size() - 1));
    }

    public SpriteTilemap addLayer(String name) {
        layers.add(new TilemapLayer(name));
        version++;
        return this;
    }

    public SpriteTilemap removeLayer(int layerIndex) {
        if (layers.size() > 1 && layerIndex >= 0 && layerIndex < layers.size()) {
            layers.remove(layerIndex);
            version++;
        }
        return this;
    }

    public SpriteTilemap moveLayer(int layerIndex, int destinationIndex) {
        if (layerIndex < 0 || layerIndex >= layers.size()
                || destinationIndex < 0 || destinationIndex >= layers.size()) {
            return this;
        }
        layers.add(destinationIndex, layers.remove(layerIndex));
        version++;
        return this;
    }

    public boolean contains(int cellX, int cellY) {
        return true;
    }

    public CellBounds usedBounds() {
        CellBounds bounds = CellBounds.empty();
        for (TilemapLayer layer : layers) {
            bounds = bounds.union(layer.usedBounds());
        }
        return bounds;
    }

    public CellBounds collisionBounds() {
        CellBounds bounds = CellBounds.empty();
        for (TilemapLayer layer : layers) {
            if (layer.collisionEnabled()) {
                bounds = bounds.union(layer.usedBounds());
            }
        }
        return bounds;
    }

    public int tileIndex(int cellX, int cellY) {
        return tileIndex(0, cellX, cellY);
    }

    public int tileIndex(int layerIndex, int cellX, int cellY) {
        return layer(layerIndex).tileIndex(cellX, cellY);
    }

    public SpriteTilemap setTile(int cellX, int cellY, int tileIndex) {
        return setTile(0, cellX, cellY, tileIndex);
    }

    public SpriteTilemap setTile(int layerIndex, int cellX, int cellY, int tileIndex) {
        if (layer(layerIndex).setTile(cellX, cellY, tileIndex)) {
            version++;
        }
        return this;
    }

    public SpriteTilemap clearTile(int cellX, int cellY) {
        return setTile(0, cellX, cellY, EMPTY_TILE_INDEX);
    }

    public SpriteTilemap clearTile(int layerIndex, int cellX, int cellY) {
        return setTile(layerIndex, cellX, cellY, EMPTY_TILE_INDEX);
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
        for (TilemapLayer layer : layers) {
            int tileIndex = layer.tileIndex(cellX, cellY);
            if (layer.collisionEnabled() && solidTiles.contains(tileIndex) && collisionShapesOf(tileIndex).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<TileCollisionShape> collisionShapesOf(int tileIndex) {
        TileData data = tileData.get(tileIndex);
        return data == null ? List.of() : data.collisionShapes();
    }

    public List<TileCollisionShape> cellCollisionShapes(int cellX, int cellY) {
        List<TileCollisionShape> shapes = new ArrayList<>();
        for (TilemapLayer layer : layers) {
            if (layer.collisionEnabled()) {
                shapes.addAll(collisionShapesOf(layer.tileIndex(cellX, cellY)));
            }
        }
        return shapes;
    }

    public Map<Integer, TileData> tileDataByIndex() {
        return Collections.unmodifiableMap(tileData);
    }

    public Optional<TileData> existingTileData(int tileIndex) {
        return Optional.ofNullable(tileData.get(tileIndex));
    }

    public TileData tileData(int tileIndex) {
        return tileData.computeIfAbsent(tileIndex, ignored -> new TileData());
    }

    public SpriteTilemap putTileData(int tileIndex, TileData data) {
        tileData.put(tileIndex, data);
        version++;
        return this;
    }

    public SpriteTilemap removeTileData(int tileIndex) {
        if (tileData.remove(tileIndex) != null) {
            version++;
        }
        return this;
    }

    public Map<Integer, String> scenesByTile() {
        return Collections.unmodifiableMap(scenePaths);
    }

    public Optional<String> sceneForTile(int tileIndex) {
        return Optional.ofNullable(scenePaths.get(tileIndex));
    }

    public SpriteTilemap setSceneForTile(int tileIndex, String path) {
        if (path.isEmpty()) {
            scenePaths.remove(tileIndex);
        } else {
            scenePaths.put(tileIndex, path);
        }
        version++;
        return this;
    }

    public List<TerrainDefinition> terrains() {
        return Collections.unmodifiableList(terrains);
    }

    public SpriteTilemap addTerrain(TerrainDefinition terrain) {
        terrains.add(terrain);
        version++;
        return this;
    }

    public SpriteTilemap removeTerrain(int terrainIndex) {
        if (terrainIndex >= 0 && terrainIndex < terrains.size()) {
            terrains.remove(terrainIndex);
            version++;
        }
        return this;
    }

    public SpriteTilemap renameTerrain(int terrainIndex, String name) {
        if (terrainIndex >= 0 && terrainIndex < terrains.size()) {
            terrains.set(terrainIndex, new TerrainDefinition(name, terrains.get(terrainIndex).color()));
            version++;
        }
        return this;
    }

    public TerrainMatchMode terrainMatchMode() {
        return terrainMatchMode;
    }

    public SpriteTilemap setTerrainMatchMode(TerrainMatchMode value) {
        if (terrainMatchMode != value) {
            terrainMatchMode = value;
            version++;
        }
        return this;
    }

    public long version() {
        return version;
    }

    public SpriteTilemap touch() {
        version++;
        return this;
    }
}
