package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.postfx.PostEffect;
import fr.epistudio.epysia.render.postfx.PostEffectInsertionPoint;
import fr.epistudio.epysia.render.postfx.PostEffectStack;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;

public final class PostEffectStackJsonCodec {

    public void writeStack(JsonWriter writer, PostEffectStack stack) {
        writer.beginArray();
        for (PostEffect effect : stack.effects()) {
            writeEffect(writer, effect);
        }
        writer.endArray();
    }

    private void writeEffect(JsonWriter writer, PostEffect effect) {
        writer.beginObject();
        writer.key("name").valueString(effect.name());
        writer.key("shader").valueString(effect.shaderPath());
        writer.key("insertionPoint").valueString(effect.insertionPoint().name());
        writer.key("enabled").valueBoolean(effect.enabled());
        writer.key("uniforms").beginObject();
        for (Map.Entry<String, ShaderUniformValue> entry : effect.uniformValues().entrySet()) {
            writer.key(entry.getKey());
            writeValue(writer, entry.getValue());
        }
        writer.endObject();
        writer.endObject();
    }

    private void writeValue(JsonWriter writer, ShaderUniformValue value) {
        writer.beginObject();
        switch (value) {
            case ShaderUniformValue.FloatValue number -> writer.key("float").valueNumber(number.value());
            case ShaderUniformValue.IntValue number -> writer.key("int").valueNumber(number.value());
            case ShaderUniformValue.BoolValue flag -> writer.key("bool").valueBoolean(flag.value());
            case ShaderUniformValue.TextureValue texture -> writer.key("texture").valueString(texture.path());
            default -> writeCompoundValue(writer, value);
        }
        writer.endObject();
    }

    private void writeCompoundValue(JsonWriter writer, ShaderUniformValue value) {
        switch (value) {
            case ShaderUniformValue.Vector2Value vector ->
                    writeFloatArray(writer, "vec2", new float[] {vector.x(), vector.y()});
            case ShaderUniformValue.Vector3Value vector ->
                    writeFloatArray(writer, "vec3", new float[] {vector.x(), vector.y(), vector.z()});
            case ShaderUniformValue.Vector4Value vector ->
                    writeFloatArray(writer, "vec4", new float[] {vector.x(), vector.y(), vector.z(), vector.w()});
            case ShaderUniformValue.Matrix4Value matrix -> writeFloatArray(writer, "mat4", matrix.columnMajorElements());
            case ShaderUniformValue.FloatArrayValue floats -> writeFloatArray(writer, "floatArray", floats.elements());
            case ShaderUniformValue.Vector4ArrayValue vectors ->
                    writeFloatArray(writer, "vec4Array", vectors.flattenedElements());
            default -> {
            }
        }
    }

    private void writeFloatArray(JsonWriter writer, String keyName, float[] elements) {
        writer.key(keyName).beginArray();
        for (float element : elements) {
            writer.valueNumber(element);
        }
        writer.endArray();
    }

    public void readStack(List<?> stackJson, PostEffectStack destination) {
        destination.clear();
        for (Object element : stackJson) {
            if (element instanceof Map<?, ?> effectJson) {
                readEffect(effectJson, destination);
            }
        }
    }

    private void readEffect(Map<?, ?> effectJson, PostEffectStack destination) {
        String name = stringValue(effectJson, "name");
        String shader = stringValue(effectJson, "shader");
        if (name.isEmpty() || shader.isEmpty()) {
            return;
        }
        PostEffect effect = destination.add(name, shader, parseInsertionPoint(effectJson));
        if (effectJson.get("enabled") instanceof Boolean enabled) {
            effect.setEnabled(enabled);
        }
        if (effectJson.get("uniforms") instanceof Map<?, ?> uniforms) {
            readUniforms(uniforms, effect);
        }
    }

    private PostEffectInsertionPoint parseInsertionPoint(Map<?, ?> effectJson) {
        try {
            return PostEffectInsertionPoint.valueOf(stringValue(effectJson, "insertionPoint"));
        } catch (IllegalArgumentException unknown) {
            return PostEffectInsertionPoint.AFTER_TONEMAP;
        }
    }

    private void readUniforms(Map<?, ?> uniforms, PostEffect effect) {
        for (Map.Entry<?, ?> entry : uniforms.entrySet()) {
            if (entry.getKey() instanceof String uniformName && entry.getValue() instanceof Map<?, ?> encoded) {
                readUniformValue(encoded).ifPresent(value -> effect.setUniformValue(uniformName, value));
            }
        }
    }

    private Optional<ShaderUniformValue> readUniformValue(Map<?, ?> encoded) {
        if (encoded.get("float") instanceof Number number) {
            return Optional.of(new ShaderUniformValue.FloatValue(number.floatValue()));
        }
        if (encoded.get("int") instanceof Number number) {
            return Optional.of(new ShaderUniformValue.IntValue(number.intValue()));
        }
        if (encoded.get("bool") instanceof Boolean flag) {
            return Optional.of(new ShaderUniformValue.BoolValue(flag));
        }
        if (encoded.get("texture") instanceof String path) {
            return Optional.of(new ShaderUniformValue.TextureValue(path));
        }
        return readCompoundValue(encoded);
    }

    private Optional<ShaderUniformValue> readCompoundValue(Map<?, ?> encoded) {
        if (encoded.get("vec2") instanceof List<?> components && components.size() >= 2) {
            float[] values = toFloatArray(components);
            return Optional.of(new ShaderUniformValue.Vector2Value(values[0], values[1]));
        }
        if (encoded.get("vec3") instanceof List<?> components && components.size() >= 3) {
            float[] values = toFloatArray(components);
            return Optional.of(new ShaderUniformValue.Vector3Value(values[0], values[1], values[2]));
        }
        if (encoded.get("vec4") instanceof List<?> components && components.size() >= 4) {
            float[] values = toFloatArray(components);
            return Optional.of(new ShaderUniformValue.Vector4Value(values[0], values[1], values[2], values[3]));
        }
        return readArrayValue(encoded);
    }

    private Optional<ShaderUniformValue> readArrayValue(Map<?, ?> encoded) {
        if (encoded.get("mat4") instanceof List<?> components && components.size() >= 16) {
            return Optional.of(new ShaderUniformValue.Matrix4Value(toFloatArray(components)));
        }
        if (encoded.get("floatArray") instanceof List<?> components) {
            return Optional.of(new ShaderUniformValue.FloatArrayValue(toFloatArray(components)));
        }
        if (encoded.get("vec4Array") instanceof List<?> components) {
            return Optional.of(new ShaderUniformValue.Vector4ArrayValue(toFloatArray(components)));
        }
        return Optional.empty();
    }

    private static float[] toFloatArray(List<?> components) {
        float[] result = new float[components.size()];
        for (int index = 0; index < components.size(); index++) {
            result[index] = components.get(index) instanceof Number number ? number.floatValue() : 0.0f;
        }
        return result;
    }

    private static String stringValue(Map<?, ?> json, String keyName) {
        return json.get(keyName) instanceof String value ? value : "";
    }
}
