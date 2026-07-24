package fr.epistudio.epysia.assets.epytilemap;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.util.List;
import java.util.Map;

public final class SpriteTilemapJsonCodec {

    private static final String ATLAS_KEY = "atlas";
    private static final String CELL_WIDTH_KEY = "cellWidth";
    private static final String CELL_HEIGHT_KEY = "cellHeight";
    private static final String WIDTH_KEY = "width";
    private static final String HEIGHT_KEY = "height";
    private static final String ROWS_KEY = "rows";
    private static final String SOLID_TILES_KEY = "solidTiles";
    private static final String RUN_SEPARATOR = ",";
    private static final char RUN_LENGTH_MARK = 'x';

    public SpriteTilemap read(String json) {
        Map<String, Object> root = new JsonReader(json).readRootObject();
        String atlasPath = root.get(ATLAS_KEY) instanceof String path ? path : "";
        SpriteTilemap tilemap = new SpriteTilemap(asInt(root.get(WIDTH_KEY)), asInt(root.get(HEIGHT_KEY)),
                asFloatOrDefault(root.get(CELL_WIDTH_KEY)), asFloatOrDefault(root.get(CELL_HEIGHT_KEY)), atlasPath);
        readRows(tilemap, root.get(ROWS_KEY));
        readSolidTiles(tilemap, root.get(SOLID_TILES_KEY));
        return tilemap;
    }

    private static void readRows(SpriteTilemap tilemap, Object value) {
        if (!(value instanceof List<?> rows)) {
            return;
        }
        for (int cellY = 0; cellY < rows.size() && cellY < tilemap.height(); cellY++) {
            if (rows.get(cellY) instanceof String encodedRow) {
                readRow(tilemap, cellY, encodedRow);
            }
        }
    }

    private static void readRow(SpriteTilemap tilemap, int cellY, String encodedRow) {
        if (encodedRow.isEmpty()) {
            return;
        }
        int cellX = 0;
        for (String run : encodedRow.split(RUN_SEPARATOR)) {
            cellX = readRun(tilemap, cellX, cellY, run);
        }
    }

    private static int readRun(SpriteTilemap tilemap, int cellX, int cellY, String run) {
        int markPosition = run.indexOf(RUN_LENGTH_MARK);
        if (markPosition <= 0) {
            return cellX;
        }
        int count = Integer.parseInt(run.substring(0, markPosition));
        int tileIndex = Integer.parseInt(run.substring(markPosition + 1));
        for (int step = 0; step < count; step++) {
            tilemap.setTile(cellX + step, cellY, tileIndex);
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

    public String write(SpriteTilemap tilemap) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key(ATLAS_KEY).valueString(tilemap.atlasPath());
        writer.key(CELL_WIDTH_KEY).valueNumber(tilemap.cellWidth());
        writer.key(CELL_HEIGHT_KEY).valueNumber(tilemap.cellHeight());
        writer.key(WIDTH_KEY).valueNumber(tilemap.width());
        writer.key(HEIGHT_KEY).valueNumber(tilemap.height());
        writeRows(writer, tilemap);
        writeSolidTiles(writer, tilemap);
        writer.endObject();
        return writer.toString();
    }

    private static void writeRows(JsonWriter writer, SpriteTilemap tilemap) {
        writer.key(ROWS_KEY).beginArray();
        for (int cellY = 0; cellY < tilemap.height(); cellY++) {
            writer.valueString(encodeRow(tilemap, cellY));
        }
        writer.endArray();
    }

    private static String encodeRow(SpriteTilemap tilemap, int cellY) {
        StringBuilder encoded = new StringBuilder();
        int cellX = 0;
        while (cellX < tilemap.width()) {
            int tileIndex = tilemap.tileIndex(cellX, cellY);
            int runEnd = cellX + 1;
            while (runEnd < tilemap.width() && tilemap.tileIndex(runEnd, cellY) == tileIndex) {
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

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static float asFloatOrDefault(Object value) {
        return value instanceof Number number ? number.floatValue() : 1.0f;
    }
}
