package fr.epistudio.epysia.render.shader;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ShaderUniformValues {

    private final Map<String, ShaderUniformValue> values = new LinkedHashMap<>();
    private long valueRevision;
    private long structureRevision;

    public Map<String, ShaderUniformValue> all() {
        return Collections.unmodifiableMap(values);
    }

    public Optional<ShaderUniformValue> value(String uniformName) {
        return Optional.ofNullable(values.get(uniformName));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void set(String uniformName, ShaderUniformValue value) {
        values.put(uniformName, value);
        if (value instanceof ShaderUniformValue.TextureValue) {
            structureRevision++;
        } else {
            valueRevision++;
        }
    }

    public void remove(String uniformName) {
        if (values.remove(uniformName) != null) {
            structureRevision++;
        }
    }

    public void clear() {
        if (!values.isEmpty()) {
            values.clear();
            structureRevision++;
        }
    }

    public void setFloat(String uniformName, float value) {
        set(uniformName, new ShaderUniformValue.FloatValue(value));
    }

    public void setInt(String uniformName, int value) {
        set(uniformName, new ShaderUniformValue.IntValue(value));
    }

    public void setBool(String uniformName, boolean value) {
        set(uniformName, new ShaderUniformValue.BoolValue(value));
    }

    public void setVector2(String uniformName, Vector2f value) {
        set(uniformName, new ShaderUniformValue.Vector2Value(value.x, value.y));
    }

    public void setVector3(String uniformName, Vector3f value) {
        set(uniformName, new ShaderUniformValue.Vector3Value(value.x, value.y, value.z));
    }

    public void setVector4(String uniformName, Vector4f value) {
        set(uniformName, new ShaderUniformValue.Vector4Value(value.x, value.y, value.z, value.w));
    }

    public void setMatrix(String uniformName, Matrix4f value) {
        float[] elements = new float[16];
        value.get(elements);
        set(uniformName, new ShaderUniformValue.Matrix4Value(elements));
    }

    public void setTexture(String uniformName, String path) {
        set(uniformName, new ShaderUniformValue.TextureValue(path));
    }

    public void setFloatArray(String uniformName, float[] elements) {
        set(uniformName, new ShaderUniformValue.FloatArrayValue(elements.clone()));
    }

    public void setVector4Array(String uniformName, Vector4f[] elements) {
        float[] flattened = new float[elements.length * 4];
        for (int index = 0; index < elements.length; index++) {
            flattened[index * 4] = elements[index].x;
            flattened[index * 4 + 1] = elements[index].y;
            flattened[index * 4 + 2] = elements[index].z;
            flattened[index * 4 + 3] = elements[index].w;
        }
        set(uniformName, new ShaderUniformValue.Vector4ArrayValue(flattened));
    }

    public long valueRevision() {
        return valueRevision;
    }

    public long structureRevision() {
        return structureRevision;
    }
}
