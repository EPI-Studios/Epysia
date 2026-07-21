package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class LitMaterial extends Material {

    @Uniform
    public final Vector3f baseColor = new Vector3f(1.0f, 1.0f, 1.0f);

    @Uniform
    public float metallic = 0.0f;

    @Uniform
    public float roughness = 0.6f;

    @Uniform
    public float emissiveStrength = 0.0f;

    @Uniform
    public float alphaCutoff = 0.0f;

    @Uniform
    public float normalScale = 1.0f;

    @Uniform
    public float occlusionStrength = 1.0f;

    @Texture(srgb = true)
    public TextureHandle albedo;

    @Texture
    public TextureHandle normalMap;

    @Texture
    public TextureHandle metallicRoughnessMap;

    @Texture
    public TextureHandle occlusionMap;

    @Texture(srgb = true)
    public TextureHandle emissiveMap;

    private final ShaderUniformValues surfaceUniforms = new ShaderUniformValues();
    private String surfaceShaderPath = "";
    private boolean animatedShadow = true;

    public LitMaterial() {
        super("lit.vert.glsl", "lit.frag.glsl");
    }

    public boolean animatedShadow() {
        return animatedShadow;
    }

    public LitMaterial setAnimatedShadow(boolean value) {
        this.animatedShadow = value;
        return this;
    }

    public String surfaceShaderPath() {
        return surfaceShaderPath;
    }

    public LitMaterial setSurfaceShaderPath(String path) {
        this.surfaceShaderPath = path;
        return this;
    }

    public ShaderUniformValues surfaceUniforms() {
        return surfaceUniforms;
    }

    public LitMaterial setFloat(String uniformName, float value) {
        surfaceUniforms.setFloat(uniformName, value);
        return this;
    }

    public LitMaterial setInt(String uniformName, int value) {
        surfaceUniforms.setInt(uniformName, value);
        return this;
    }

    public LitMaterial setBool(String uniformName, boolean value) {
        surfaceUniforms.setBool(uniformName, value);
        return this;
    }

    public LitMaterial setVector2(String uniformName, Vector2f value) {
        surfaceUniforms.setVector2(uniformName, value);
        return this;
    }

    public LitMaterial setVector3(String uniformName, Vector3f value) {
        surfaceUniforms.setVector3(uniformName, value);
        return this;
    }

    public LitMaterial setVector4(String uniformName, Vector4f value) {
        surfaceUniforms.setVector4(uniformName, value);
        return this;
    }

    public LitMaterial setColor(String uniformName, Vector3f value) {
        surfaceUniforms.setVector3(uniformName, value);
        return this;
    }

    public LitMaterial setMatrix(String uniformName, Matrix4f value) {
        surfaceUniforms.setMatrix(uniformName, value);
        return this;
    }

    public LitMaterial setTexture(String uniformName, String path) {
        surfaceUniforms.setTexture(uniformName, path);
        return this;
    }

    public LitMaterial setBaseColor(float red, float green, float blue) {
        baseColor.set(red, green, blue);
        return this;
    }

    public LitMaterial setMetallic(float value) {
        this.metallic = value;
        return this;
    }

    public LitMaterial setRoughness(float value) {
        this.roughness = value;
        return this;
    }

    public LitMaterial setEmissiveStrength(float value) {
        this.emissiveStrength = value;
        return this;
    }

    public LitMaterial setAlphaCutoff(float value) {
        this.alphaCutoff = value;
        return this;
    }

    public LitMaterial setNormalScale(float value) {
        this.normalScale = value;
        return this;
    }

    public LitMaterial setOcclusionStrength(float value) {
        this.occlusionStrength = value;
        return this;
    }

    public LitMaterial setAlbedo(TextureHandle texture) {
        this.albedo = texture;
        return this;
    }

    public LitMaterial setNormalMap(TextureHandle texture) {
        this.normalMap = texture;
        return this;
    }

    public LitMaterial setMetallicRoughnessMap(TextureHandle texture) {
        this.metallicRoughnessMap = texture;
        return this;
    }

    public LitMaterial setOcclusionMap(TextureHandle texture) {
        this.occlusionMap = texture;
        return this;
    }

    public LitMaterial setEmissiveMap(TextureHandle texture) {
        this.emissiveMap = texture;
        return this;
    }
}
