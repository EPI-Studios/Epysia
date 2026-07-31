package fr.epistudio.epysia.assets.epyimpostor;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;
import org.joml.Vector3f;

import java.util.Map;

public final class ImpostorAtlasJsonCodec {

    private static final String VERSION_KEY = "version";
    private static final String MAPPING_KEY = "mapping";
    private static final String GRID_SIZE_KEY = "gridSize";
    private static final String TILE_SIZE_KEY = "tileSize";
    private static final String RADIUS_KEY = "radius";
    private static final String CENTER_KEY = "center";
    private static final String ALBEDO_ATLAS_KEY = "albedoAtlas";
    private static final String NORMAL_ATLAS_KEY = "normalAtlas";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";

    public String write(ImpostorAtlas atlas) {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key(VERSION_KEY).valueNumber(EpyImpostorFormat.VERSION);
        writer.key(MAPPING_KEY).valueString(atlas.mapping());
        writer.key(GRID_SIZE_KEY).valueNumber(atlas.gridSize());
        writer.key(TILE_SIZE_KEY).valueNumber(atlas.tileSize());
        writer.key(RADIUS_KEY).valueNumber(atlas.radius());
        writeCenter(writer, atlas.center());
        writer.key(ALBEDO_ATLAS_KEY).valueString(atlas.albedoAtlasPath());
        writer.key(NORMAL_ATLAS_KEY).valueString(atlas.normalAtlasPath());
        writer.endObject();
        return writer.toString();
    }

    private static void writeCenter(JsonWriter writer, Vector3f center) {
        writer.key(CENTER_KEY).beginObject();
        writer.key(X_KEY).valueNumber(center.x);
        writer.key(Y_KEY).valueNumber(center.y);
        writer.key(Z_KEY).valueNumber(center.z);
        writer.endObject();
    }

    public ImpostorAtlas read(String json) {
        Map<String, Object> root = new JsonReader(json).readRootObject();
        return new ImpostorAtlas(
                asString(root.get(MAPPING_KEY), EpyImpostorFormat.HEMI_OCTAHEDRAL_MAPPING),
                asInt(root.get(GRID_SIZE_KEY)),
                asInt(root.get(TILE_SIZE_KEY)),
                asFloat(root.get(RADIUS_KEY)),
                readCenter(root.get(CENTER_KEY)),
                asString(root.get(ALBEDO_ATLAS_KEY), ""),
                asString(root.get(NORMAL_ATLAS_KEY), ""));
    }

    private static Vector3f readCenter(Object value) {
        if (!(value instanceof Map<?, ?> center)) {
            return new Vector3f();
        }
        return new Vector3f(asFloat(center.get(X_KEY)), asFloat(center.get(Y_KEY)), asFloat(center.get(Z_KEY)));
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }
}
