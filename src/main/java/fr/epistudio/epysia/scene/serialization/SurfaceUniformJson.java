package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;

import java.util.Map;

public final class SurfaceUniformJson {

    private SurfaceUniformJson() {
    }

    public static void write(JsonWriter writer, ShaderUniformValues values) {
        writer.beginObject();
        for (Map.Entry<String, ShaderUniformValue> entry : values.all().entrySet()) {
            writeOne(writer, entry.getKey(), entry.getValue());
        }
        writer.endObject();
    }

    public static void writeOne(JsonWriter writer, String name, ShaderUniformValue value) {
        writer.key(name).beginObject().key("type").valueString(SurfaceUniformTypeTags.of(value));
        switch (value) {
            case ShaderUniformValue.FloatValue number -> writer.key("value").valueNumber(number.value());
            case ShaderUniformValue.IntValue number -> writer.key("value").valueNumber(number.value());
            case ShaderUniformValue.BoolValue flag -> writer.key("value").valueBoolean(flag.value());
            case ShaderUniformValue.TextureValue texture -> writer.key("value").valueString(texture.path());
            case ShaderUniformValue.Vector2Value vector -> components(writer, vector.x(), vector.y());
            case ShaderUniformValue.Vector3Value vector -> components(writer, vector.x(), vector.y(), vector.z());
            case ShaderUniformValue.Vector4Value vector ->
                    components(writer, vector.x(), vector.y(), vector.z(), vector.w());
            case ShaderUniformValue.Matrix4Value matrix -> components(writer, matrix.columnMajorElements());
            case ShaderUniformValue.FloatArrayValue floats -> components(writer, floats.elements());
            case ShaderUniformValue.Vector4ArrayValue vectors -> components(writer, vectors.flattenedElements());
        }
        writer.endObject();
    }

    private static void components(JsonWriter writer, float... elements) {
        writer.key("value").beginArray();
        for (float element : elements) {
            writer.valueNumber(element);
        }
        writer.endArray();
    }

    @SuppressWarnings("unchecked")
    public static void apply(ShaderUniformValues target, Map<String, Object> encoded) {
        for (Map.Entry<String, Object> entry : encoded.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> fields) {
                applyOne(target, entry.getKey(), (Map<String, Object>) fields);
            }
        }
    }

    private static void applyOne(ShaderUniformValues target, String name, Map<String, Object> encoded) {
        String tag = encoded.get("type") instanceof String type ? type : "";
        SurfaceUniformTypeTags.parse(tag, encoded.get("value")).ifPresent(value -> target.set(name, value));
    }
}
