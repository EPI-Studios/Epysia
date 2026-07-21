package fr.epistudio.epysia.render.postfx;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.Optional;
import fr.epistudio.epysia.render.shader.ShaderUniformValue;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;

public final class PostEffect {

    private final String name;
    private String shaderPath;
    private PostEffectInsertionPoint insertionPoint;
    private boolean enabled = true;
    private final ShaderUniformValues uniformValues = new ShaderUniformValues();
    private long settingsRevision;

    public PostEffect(String name, String shaderPath, PostEffectInsertionPoint insertionPoint) {
        this.name = name;
        this.shaderPath = shaderPath;
        this.insertionPoint = insertionPoint;
    }

    public String name() {
        return name;
    }

    public String shaderPath() {
        return shaderPath;
    }

    public PostEffect setShaderPath(String path) {
        this.shaderPath = path;
        settingsRevision++;
        return this;
    }

    public PostEffectInsertionPoint insertionPoint() {
        return insertionPoint;
    }

    public PostEffect setInsertionPoint(PostEffectInsertionPoint point) {
        this.insertionPoint = point;
        settingsRevision++;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public PostEffect setEnabled(boolean value) {
        this.enabled = value;
        settingsRevision++;
        return this;
    }

    public Map<String, ShaderUniformValue> uniformValues() {
        return uniformValues.all();
    }

    public Optional<ShaderUniformValue> uniformValue(String uniformName) {
        return uniformValues.value(uniformName);
    }

    public PostEffect setUniformValue(String uniformName, ShaderUniformValue value) {
        uniformValues.set(uniformName, value);
        return this;
    }

    public PostEffect setFloat(String uniformName, float value) {
        return setUniformValue(uniformName, new ShaderUniformValue.FloatValue(value));
    }

    public PostEffect setInt(String uniformName, int value) {
        return setUniformValue(uniformName, new ShaderUniformValue.IntValue(value));
    }

    public PostEffect setBool(String uniformName, boolean value) {
        return setUniformValue(uniformName, new ShaderUniformValue.BoolValue(value));
    }

    public PostEffect setVector2(String uniformName, Vector2f value) {
        return setUniformValue(uniformName, new ShaderUniformValue.Vector2Value(value.x, value.y));
    }

    public PostEffect setVector3(String uniformName, Vector3f value) {
        return setUniformValue(uniformName, new ShaderUniformValue.Vector3Value(value.x, value.y, value.z));
    }

    public PostEffect setVector4(String uniformName, Vector4f value) {
        return setUniformValue(uniformName, new ShaderUniformValue.Vector4Value(value.x, value.y, value.z, value.w));
    }

    public PostEffect setColor(String uniformName, Vector3f value) {
        return setVector3(uniformName, value);
    }

    public PostEffect setColor(String uniformName, Vector4f value) {
        return setVector4(uniformName, value);
    }

    public PostEffect setMatrix(String uniformName, Matrix4f value) {
        uniformValues.setMatrix(uniformName, value);
        return this;
    }

    public PostEffect setTexture(String uniformName, String path) {
        return setUniformValue(uniformName, new ShaderUniformValue.TextureValue(path));
    }

    public PostEffect setFloatArray(String uniformName, float[] values) {
        uniformValues.setFloatArray(uniformName, values);
        return this;
    }

    public PostEffect setVector4Array(String uniformName, Vector4f[] values) {
        uniformValues.setVector4Array(uniformName, values);
        return this;
    }

    public long valueRevision() {
        return uniformValues.valueRevision();
    }

    public long structureRevision() {
        return settingsRevision + uniformValues.structureRevision();
    }
}
