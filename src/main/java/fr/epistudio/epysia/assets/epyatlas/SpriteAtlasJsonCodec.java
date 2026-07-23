package fr.epistudio.epysia.assets.epyatlas;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpriteAtlasJsonCodec {

    private static final String TEXTURE_KEY = "texture";
    private static final String GRID_KEY = "grid";
    private static final String REGIONS_KEY = "regions";
    private static final String CELL_WIDTH_KEY = "cellWidth";
    private static final String CELL_HEIGHT_KEY = "cellHeight";
    private static final String COLUMNS_KEY = "columns";
    private static final String ROWS_KEY = "rows";
    private static final String NAME_KEY = "name";
    private static final String MIN_U_KEY = "u0";
    private static final String MIN_V_KEY = "v0";
    private static final String MAX_U_KEY = "u1";
    private static final String MAX_V_KEY = "v1";

    public SpriteAtlas read(String json) {
        Map<String, Object> root = new JsonReader(json).readRootObject();
        String texturePath = root.get(TEXTURE_KEY) instanceof String path ? path : "";
        List<SpriteAtlasRegion> regions = readRegions(root.get(REGIONS_KEY));
        return readGrid(root.get(GRID_KEY))
                .map(grid -> SpriteAtlas.gridAtlas(texturePath, grid, regions))
                .orElseGet(() -> new SpriteAtlas(texturePath, regions));
    }

    private static Optional<SpriteAtlasGrid> readGrid(Object value) {
        if (!(value instanceof Map<?, ?> grid)) {
            return Optional.empty();
        }
        int columns = asInt(grid.get(COLUMNS_KEY));
        int rows = asInt(grid.get(ROWS_KEY));
        if (columns <= 0 || rows <= 0) {
            return Optional.empty();
        }
        return Optional.of(new SpriteAtlasGrid(asInt(grid.get(CELL_WIDTH_KEY)),
                asInt(grid.get(CELL_HEIGHT_KEY)), columns, rows));
    }

    private static List<SpriteAtlasRegion> readRegions(Object value) {
        List<SpriteAtlasRegion> regions = new ArrayList<>();
        if (!(value instanceof List<?> entries)) {
            return regions;
        }
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> region && region.get(NAME_KEY) instanceof String name) {
                regions.add(new SpriteAtlasRegion(name, asFloat(region.get(MIN_U_KEY)),
                        asFloat(region.get(MIN_V_KEY)), asFloat(region.get(MAX_U_KEY)),
                        asFloat(region.get(MAX_V_KEY))));
            }
        }
        return regions;
    }

    public String write(SpriteAtlas atlas) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key(TEXTURE_KEY).valueString(atlas.texturePath());
        atlas.grid().ifPresent(grid -> writeGrid(writer, grid));
        writeRegions(writer, atlas.explicitRegions());
        writer.endObject();
        return writer.toString();
    }

    private static void writeGrid(JsonWriter writer, SpriteAtlasGrid grid) {
        writer.key(GRID_KEY).beginObject();
        writer.key(CELL_WIDTH_KEY).valueNumber(grid.cellWidth());
        writer.key(CELL_HEIGHT_KEY).valueNumber(grid.cellHeight());
        writer.key(COLUMNS_KEY).valueNumber(grid.columns());
        writer.key(ROWS_KEY).valueNumber(grid.rows());
        writer.endObject();
    }

    private static void writeRegions(JsonWriter writer, List<SpriteAtlasRegion> regions) {
        writer.key(REGIONS_KEY).beginArray();
        for (SpriteAtlasRegion region : regions) {
            writer.beginObject();
            writer.key(NAME_KEY).valueString(region.name());
            writer.key(MIN_U_KEY).valueNumber(region.minU());
            writer.key(MIN_V_KEY).valueNumber(region.minV());
            writer.key(MAX_U_KEY).valueNumber(region.maxU());
            writer.key(MAX_V_KEY).valueNumber(region.maxV());
            writer.endObject();
        }
        writer.endArray();
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }
}
