package fr.epistudio.epysia.render.shader;

public sealed interface ShaderUniformValue {

    record FloatValue(float value) implements ShaderUniformValue {
    }

    record IntValue(int value) implements ShaderUniformValue {
    }

    record BoolValue(boolean value) implements ShaderUniformValue {
    }

    record Vector2Value(float x, float y) implements ShaderUniformValue {
    }

    record Vector3Value(float x, float y, float z) implements ShaderUniformValue {
    }

    record Vector4Value(float x, float y, float z, float w) implements ShaderUniformValue {
    }

    record Matrix4Value(float[] columnMajorElements) implements ShaderUniformValue {
    }

    record FloatArrayValue(float[] elements) implements ShaderUniformValue {
    }

    record Vector4ArrayValue(float[] flattenedElements) implements ShaderUniformValue {
    }

    record TextureValue(String path) implements ShaderUniformValue {
    }
}
