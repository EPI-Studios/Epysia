package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.shader.ShaderUniformValue;

import java.util.List;
import java.util.Optional;

final class SurfaceUniformTypeTags {

    private static final String FLOAT = "float";
    private static final String INT = "int";
    private static final String BOOL = "bool";
    private static final String VECTOR2 = "vec2";
    private static final String VECTOR3 = "vec3";
    private static final String VECTOR4 = "vec4";
    private static final String MATRIX4 = "mat4";
    private static final String TEXTURE = "texture";
    private static final String FLOAT_ARRAY = "floatArray";
    private static final String VECTOR4_ARRAY = "vec4Array";

    private SurfaceUniformTypeTags() {
    }

    static String of(ShaderUniformValue value) {
        return switch (value) {
            case ShaderUniformValue.FloatValue ignored -> FLOAT;
            case ShaderUniformValue.IntValue ignored -> INT;
            case ShaderUniformValue.BoolValue ignored -> BOOL;
            case ShaderUniformValue.Vector2Value ignored -> VECTOR2;
            case ShaderUniformValue.Vector3Value ignored -> VECTOR3;
            case ShaderUniformValue.Vector4Value ignored -> VECTOR4;
            case ShaderUniformValue.Matrix4Value ignored -> MATRIX4;
            case ShaderUniformValue.TextureValue ignored -> TEXTURE;
            case ShaderUniformValue.FloatArrayValue ignored -> FLOAT_ARRAY;
            case ShaderUniformValue.Vector4ArrayValue ignored -> VECTOR4_ARRAY;
        };
    }

    static Optional<ShaderUniformValue> parse(String tag, Object value) {
        return switch (tag) {
            case FLOAT -> Optional.of(new ShaderUniformValue.FloatValue(number(value)));
            case INT -> Optional.of(new ShaderUniformValue.IntValue((int) number(value)));
            case BOOL -> Optional.of(new ShaderUniformValue.BoolValue(value instanceof Boolean flag && flag));
            case TEXTURE -> Optional.of(new ShaderUniformValue.TextureValue(value instanceof String path ? path : ""));
            case VECTOR2, VECTOR3, VECTOR4, MATRIX4, FLOAT_ARRAY, VECTOR4_ARRAY -> fromComponents(tag, components(value));
            default -> Optional.empty();
        };
    }

    private static Optional<ShaderUniformValue> fromComponents(String tag, float[] parts) {
        return switch (tag) {
            case VECTOR2 -> Optional.of(new ShaderUniformValue.Vector2Value(at(parts, 0), at(parts, 1)));
            case VECTOR3 -> Optional.of(new ShaderUniformValue.Vector3Value(at(parts, 0), at(parts, 1), at(parts, 2)));
            case VECTOR4 -> Optional.of(new ShaderUniformValue.Vector4Value(
                    at(parts, 0), at(parts, 1), at(parts, 2), at(parts, 3)));
            case MATRIX4 -> Optional.of(new ShaderUniformValue.Matrix4Value(resized(parts, 16)));
            case FLOAT_ARRAY -> Optional.of(new ShaderUniformValue.FloatArrayValue(parts));
            case VECTOR4_ARRAY -> Optional.of(new ShaderUniformValue.Vector4ArrayValue(parts));
            default -> Optional.empty();
        };
    }

    private static float[] components(Object value) {
        if (!(value instanceof List<?> list)) {
            return new float[0];
        }
        float[] parts = new float[list.size()];
        for (int index = 0; index < list.size(); index++) {
            parts[index] = number(list.get(index));
        }
        return parts;
    }

    private static float[] resized(float[] parts, int length) {
        float[] result = new float[length];
        System.arraycopy(parts, 0, result, 0, Math.min(parts.length, length));
        return result;
    }

    private static float at(float[] parts, int index) {
        return index < parts.length ? parts[index] : 0.0f;
    }

    private static float number(Object value) {
        return value instanceof Number numeric ? numeric.floatValue() : 0.0f;
    }
}
