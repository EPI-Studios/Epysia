package fr.epistudio.epysia.assets.epytilemap;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpriteTilemapJsonCodec {

    private static final String ATLAS_KEY = "atlas";
    private static final String CELL_WIDTH_KEY = "cellWidth";
    private static final String CELL_HEIGHT_KEY = "cellHeight";
    private static final String WIDTH_KEY = "width";
    private static final String HEIGHT_KEY = "height";
    private static final String ROWS_KEY = "rows";
    private static final String LAYERS_KEY = "layers";
    private static final String SOLID_TILES_KEY = "solidTiles";
    private static final String TILES_KEY = "tiles";
    private static final String SCENES_KEY = "scenes";
    private static final String ORIGIN_KEY = "origin";
    private static final String TERRAINS_KEY = "terrains";
    private static final String TERRAIN_MODE_KEY = "terrainMode";
    private static final String NAME_KEY = "name";
    private static final String COLOR_KEY = "color";
    private static final String VISIBLE_KEY = "visible";
    private static final String COLLISION_KEY = "collision";
    private static final String SORTING_ORDER_KEY = "sortingOrder";
    private static final String MODULATE_KEY = "modulate";
    private static final String POINTS_KEY = "points";
    private static final String ONE_WAY_KEY = "oneWay";
    private static final String ONE_WAY_MARGIN_KEY = "oneWayMargin";
    private static final String TERRAIN_KEY = "terrain";
    private static final String PEERING_KEY = "peering";
    private static final String FLIP_HORIZONTAL_KEY = "flipH";
    private static final String FLIP_VERTICAL_KEY = "flipV";
    private static final String TRANSPOSE_KEY = "transpose";
    private static final String PROBABILITY_KEY = "probability";
    private static final String Z_INDEX_KEY = "zIndex";
    private static final String CUSTOM_KEY = "custom";
    private static final String RUN_SEPARATOR = ",";
    private static final char RUN_LENGTH_MARK = 'x';

    public SpriteTilemap read(String json) {
        Map<String, Object> root = new JsonReader(json).readRootObject();
        String atlasPath = root.get(ATLAS_KEY) instanceof String path ? path : "";
        SpriteTilemap tilemap = new SpriteTilemap(asInt(root.get(WIDTH_KEY)), asInt(root.get(HEIGHT_KEY)),
                asFloatOrDefault(root.get(CELL_WIDTH_KEY)), asFloatOrDefault(root.get(CELL_HEIGHT_KEY)), atlasPath);
        readLayers(tilemap, root);
        readSolidTiles(tilemap, root.get(SOLID_TILES_KEY));
        readTerrains(tilemap, root);
        readTileData(tilemap, root.get(TILES_KEY));
        readScenes(tilemap, root.get(SCENES_KEY));
        return tilemap;
    }

    private static void readLayers(SpriteTilemap tilemap, Map<String, Object> root) {
        if (!(root.get(LAYERS_KEY) instanceof List<?> encodedLayers) || encodedLayers.isEmpty()) {
            readRows(tilemap, 0, root.get(ROWS_KEY), new int[]{0, 0});
            return;
        }
        for (int layerIndex = 0; layerIndex < encodedLayers.size(); layerIndex++) {
            if (layerIndex > 0) {
                tilemap.addLayer("Layer " + (layerIndex + 1));
            }
            if (encodedLayers.get(layerIndex) instanceof Map<?, ?> encodedLayer) {
                readLayer(tilemap, layerIndex, encodedLayer);
            }
        }
    }

    private static void readLayer(SpriteTilemap tilemap, int layerIndex, Map<?, ?> encoded) {
        TilemapLayer layer = tilemap.layer(layerIndex);
        if (encoded.get(NAME_KEY) instanceof String name) {
            layer.setName(name);
        }
        layer.setVisible(asBooleanOrDefault(encoded.get(VISIBLE_KEY), true));
        layer.setCollisionEnabled(asBooleanOrDefault(encoded.get(COLLISION_KEY), true));
        layer.setSortingOrder(asInt(encoded.get(SORTING_ORDER_KEY)));
        Vector4f modulate = asColorOrWhite(encoded.get(MODULATE_KEY));
        layer.setModulate(modulate.x, modulate.y, modulate.z, modulate.w);
        readRows(tilemap, layerIndex, encoded.get(ROWS_KEY), originOf(encoded));
    }

    private static int[] originOf(Map<?, ?> encoded) {
        if (encoded.get(ORIGIN_KEY) instanceof List<?> origin && origin.size() >= 2) {
            return new int[]{asInt(origin.get(0)), asInt(origin.get(1))};
        }
        return new int[]{0, 0};
    }

    private static void readRows(SpriteTilemap tilemap, int layerIndex, Object value, int[] origin) {
        if (!(value instanceof List<?> rows)) {
            return;
        }
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row) instanceof String encodedRow) {
                readRow(tilemap, layerIndex, origin[1] + row, encodedRow, origin[0]);
            }
        }
    }

    private static void readRow(SpriteTilemap tilemap, int layerIndex, int cellY, String encodedRow, int originX) {
        if (encodedRow.isEmpty()) {
            return;
        }
        int cellX = originX;
        for (String run : encodedRow.split(RUN_SEPARATOR)) {
            cellX = readRun(tilemap, layerIndex, cellX, cellY, run);
        }
    }

    private static int readRun(SpriteTilemap tilemap, int layerIndex, int cellX, int cellY, String run) {
        int markPosition = run.indexOf(RUN_LENGTH_MARK);
        if (markPosition <= 0) {
            return cellX;
        }
        int count = Integer.parseInt(run.substring(0, markPosition));
        int tileIndex = Integer.parseInt(run.substring(markPosition + 1));
        for (int step = 0; step < count; step++) {
            tilemap.setTile(layerIndex, cellX + step, cellY, tileIndex);
        }
        return cellX + count;
    }

    private static void readSolidTiles(SpriteTilemap tilemap, Object value) {
        if (!(value instanceof List<?> entries)) {
            return;
        }
        for (Object entry : entries) {
            if (entry instanceof Number tileIndex) {
                tilemap.setSolid(tileIndex.intValue(), true);
            }
        }
    }

    private static void readTerrains(SpriteTilemap tilemap, Map<String, Object> root) {
        if (root.get(TERRAIN_MODE_KEY) instanceof String mode) {
            tilemap.setTerrainMatchMode(TerrainMatchMode.valueOf(mode));
        }
        if (!(root.get(TERRAINS_KEY) instanceof List<?> entries)) {
            return;
        }
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> encoded && encoded.get(NAME_KEY) instanceof String name) {
                tilemap.addTerrain(new TerrainDefinition(name, asColorOrWhite(encoded.get(COLOR_KEY))));
            }
        }
    }

    private static void readTileData(SpriteTilemap tilemap, Object value) {
        if (!(value instanceof Map<?, ?> entries)) {
            return;
        }
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof Map<?, ?> encoded) {
                tilemap.putTileData(Integer.parseInt(key), readSingleTileData(encoded));
            }
        }
    }

    private static TileData readSingleTileData(Map<?, ?> encoded) {
        TileData data = new TileData();
        readCollisionShapes(data, encoded.get(COLLISION_KEY));
        readPeering(data, encoded.get(PEERING_KEY));
        readCustomData(data, encoded.get(CUSTOM_KEY));
        Vector4f modulate = asColorOrWhite(encoded.get(MODULATE_KEY));
        return data.setTerrain(asIntOrDefault(encoded.get(TERRAIN_KEY), TileData.NO_TERRAIN))
                .setFlipHorizontal(asBooleanOrDefault(encoded.get(FLIP_HORIZONTAL_KEY), false))
                .setFlipVertical(asBooleanOrDefault(encoded.get(FLIP_VERTICAL_KEY), false))
                .setTranspose(asBooleanOrDefault(encoded.get(TRANSPOSE_KEY), false))
                .setProbability(asFloatOrDefault(encoded.get(PROBABILITY_KEY)))
                .setZIndex(asInt(encoded.get(Z_INDEX_KEY)))
                .setModulate(modulate.x, modulate.y, modulate.z, modulate.w);
    }

    private static void readCollisionShapes(TileData data, Object value) {
        if (!(value instanceof List<?> shapes)) {
            return;
        }
        for (Object shape : shapes) {
            if (shape instanceof Map<?, ?> encoded) {
                data.addCollisionShape(new TileCollisionShape(readPoints(encoded.get(POINTS_KEY)),
                        asBooleanOrDefault(encoded.get(ONE_WAY_KEY), false),
                        asIntOrDefault(encoded.get(ONE_WAY_MARGIN_KEY), 0)));
            }
        }
    }

    private static List<Vector2f> readPoints(Object value) {
        List<Vector2f> points = new ArrayList<>();
        if (!(value instanceof List<?> numbers)) {
            return points;
        }
        for (int index = 0; index + 1 < numbers.size(); index += 2) {
            points.add(new Vector2f(asFloat(numbers.get(index)), asFloat(numbers.get(index + 1))));
        }
        return points;
    }

    private static void readPeering(TileData data, Object value) {
        if (!(value instanceof List<?> entries)) {
            return;
        }
        TileNeighbor[] neighbors = TileNeighbor.values();
        for (int index = 0; index < entries.size() && index < neighbors.length; index++) {
            data.setPeeringTerrain(neighbors[index], asIntOrDefault(entries.get(index), TileData.NO_TERRAIN));
        }
    }

    private static void readCustomData(TileData data, Object value) {
        if (!(value instanceof Map<?, ?> entries)) {
            return;
        }
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String stored) {
                data.setCustomValue(key, stored);
            }
        }
    }

    private static void readScenes(SpriteTilemap tilemap, Object value) {
        if (!(value instanceof Map<?, ?> entries)) {
            return;
        }
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String path) {
                tilemap.setSceneForTile(Integer.parseInt(key), path);
            }
        }
    }

    public String write(SpriteTilemap tilemap) {
        return write(tilemap, tilemap.atlasPath());
    }

    public String write(SpriteTilemap tilemap, String atlasPath) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key(ATLAS_KEY).valueString(atlasPath);
        writer.key(CELL_WIDTH_KEY).valueNumber(tilemap.cellWidth());
        writer.key(CELL_HEIGHT_KEY).valueNumber(tilemap.cellHeight());
        writer.key(WIDTH_KEY).valueNumber(tilemap.width());
        writer.key(HEIGHT_KEY).valueNumber(tilemap.height());
        writeLayers(writer, tilemap);
        writeSolidTiles(writer, tilemap);
        writeTerrains(writer, tilemap);
        writeTileData(writer, tilemap);
        writeScenes(writer, tilemap);
        writer.endObject();
        return writer.toString();
    }

    private static void writeLayers(JsonWriter writer, SpriteTilemap tilemap) {
        writer.key(LAYERS_KEY).beginArray();
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            writeLayer(writer, tilemap, layerIndex);
        }
        writer.endArray();
    }

    private static void writeLayer(JsonWriter writer, SpriteTilemap tilemap, int layerIndex) {
        TilemapLayer layer = tilemap.layer(layerIndex);
        writer.beginObject();
        writer.key(NAME_KEY).valueString(layer.name());
        writer.key(VISIBLE_KEY).valueBoolean(layer.visible());
        writer.key(COLLISION_KEY).valueBoolean(layer.collisionEnabled());
        writer.key(SORTING_ORDER_KEY).valueNumber(layer.sortingOrder());
        writeColor(writer, MODULATE_KEY, layer.modulate());
        CellBounds bounds = layer.usedBounds();
        writer.key(ORIGIN_KEY).beginArray();
        writer.valueNumber(bounds.isEmpty() ? 0 : bounds.minX());
        writer.valueNumber(bounds.isEmpty() ? 0 : bounds.minY());
        writer.endArray();
        writer.key(ROWS_KEY).beginArray();
        for (int cellY = bounds.minY(); cellY <= bounds.maxY(); cellY++) {
            writer.valueString(encodeRow(tilemap, layerIndex, cellY, bounds));
        }
        writer.endArray();
        writer.endObject();
    }

    private static String encodeRow(SpriteTilemap tilemap, int layerIndex, int cellY, CellBounds bounds) {
        StringBuilder encoded = new StringBuilder();
        int cellX = bounds.minX();
        while (cellX <= bounds.maxX()) {
            int tileIndex = tilemap.tileIndex(layerIndex, cellX, cellY);
            int runEnd = cellX + 1;
            while (runEnd <= bounds.maxX() && tilemap.tileIndex(layerIndex, runEnd, cellY) == tileIndex) {
                runEnd++;
            }
            appendRun(encoded, runEnd - cellX, tileIndex);
            cellX = runEnd;
        }
        return encoded.toString();
    }

    private static void appendRun(StringBuilder encoded, int count, int tileIndex) {
        if (!encoded.isEmpty()) {
            encoded.append(RUN_SEPARATOR);
        }
        encoded.append(count).append(RUN_LENGTH_MARK).append(tileIndex);
    }

    private static void writeSolidTiles(JsonWriter writer, SpriteTilemap tilemap) {
        if (tilemap.solidTiles().isEmpty()) {
            return;
        }
        writer.key(SOLID_TILES_KEY).beginArray();
        for (int tileIndex : tilemap.solidTiles()) {
            writer.valueNumber(tileIndex);
        }
        writer.endArray();
    }

    private static void writeTerrains(JsonWriter writer, SpriteTilemap tilemap) {
        if (tilemap.terrains().isEmpty()) {
            return;
        }
        writer.key(TERRAIN_MODE_KEY).valueString(tilemap.terrainMatchMode().name());
        writer.key(TERRAINS_KEY).beginArray();
        for (TerrainDefinition terrain : tilemap.terrains()) {
            writer.beginObject();
            writer.key(NAME_KEY).valueString(terrain.name());
            writeColor(writer, COLOR_KEY, terrain.color());
            writer.endObject();
        }
        writer.endArray();
    }

    private static void writeTileData(JsonWriter writer, SpriteTilemap tilemap) {
        Map<Integer, TileData> entries = tilemap.tileDataByIndex();
        if (entries.values().stream().allMatch(TileData::defaultValued)) {
            return;
        }
        writer.key(TILES_KEY).beginObject();
        for (Map.Entry<Integer, TileData> entry : entries.entrySet()) {
            if (!entry.getValue().defaultValued()) {
                writer.key(Integer.toString(entry.getKey()));
                writeSingleTileData(writer, entry.getValue());
            }
        }
        writer.endObject();
    }

    private static void writeSingleTileData(JsonWriter writer, TileData data) {
        writer.beginObject();
        writeCollisionShapes(writer, data);
        writeTerrainBits(writer, data);
        writer.key(FLIP_HORIZONTAL_KEY).valueBoolean(data.flipHorizontal());
        writer.key(FLIP_VERTICAL_KEY).valueBoolean(data.flipVertical());
        writer.key(TRANSPOSE_KEY).valueBoolean(data.transpose());
        writer.key(PROBABILITY_KEY).valueNumber(data.probability());
        writer.key(Z_INDEX_KEY).valueNumber(data.zIndex());
        writeColor(writer, MODULATE_KEY, data.modulate());
        writeCustomData(writer, data);
        writer.endObject();
    }

    private static void writeCollisionShapes(JsonWriter writer, TileData data) {
        if (data.collisionShapes().isEmpty()) {
            return;
        }
        writer.key(COLLISION_KEY).beginArray();
        for (TileCollisionShape shape : data.collisionShapes()) {
            writer.beginObject();
            writer.key(POINTS_KEY).beginArray();
            for (Vector2f point : shape.points()) {
                writer.valueNumber(point.x).valueNumber(point.y);
            }
            writer.endArray();
            writer.key(ONE_WAY_KEY).valueBoolean(shape.oneWay());
            writer.key(ONE_WAY_MARGIN_KEY).valueNumber(shape.oneWayMargin());
            writer.endObject();
        }
        writer.endArray();
    }

    private static void writeTerrainBits(JsonWriter writer, TileData data) {
        if (!data.participatesInTerrain()) {
            return;
        }
        writer.key(TERRAIN_KEY).valueNumber(data.terrain());
        writer.key(PEERING_KEY).beginArray();
        for (TileNeighbor neighbor : TileNeighbor.values()) {
            writer.valueNumber(data.peeringTerrain(neighbor));
        }
        writer.endArray();
    }

    private static void writeCustomData(JsonWriter writer, TileData data) {
        if (data.customData().isEmpty()) {
            return;
        }
        writer.key(CUSTOM_KEY).beginObject();
        for (Map.Entry<String, String> entry : data.customData().entrySet()) {
            writer.key(entry.getKey()).valueString(entry.getValue());
        }
        writer.endObject();
    }

    private static void writeScenes(JsonWriter writer, SpriteTilemap tilemap) {
        if (tilemap.scenesByTile().isEmpty()) {
            return;
        }
        writer.key(SCENES_KEY).beginObject();
        for (Map.Entry<Integer, String> entry : tilemap.scenesByTile().entrySet()) {
            writer.key(Integer.toString(entry.getKey())).valueString(entry.getValue());
        }
        writer.endObject();
    }

    private static void writeColor(JsonWriter writer, String key, Vector4f color) {
        writer.key(key).beginArray();
        writer.valueNumber(color.x).valueNumber(color.y).valueNumber(color.z).valueNumber(color.w);
        writer.endArray();
    }

    private static Vector4f asColorOrWhite(Object value) {
        if (!(value instanceof List<?> entries) || entries.size() < 4) {
            return new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
        return new Vector4f(asFloat(entries.get(0)), asFloat(entries.get(1)),
                asFloat(entries.get(2)), asFloat(entries.get(3)));
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int asIntOrDefault(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static float asFloatOrDefault(Object value) {
        return value instanceof Number number ? number.floatValue() : 1.0f;
    }

    private static boolean asBooleanOrDefault(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
