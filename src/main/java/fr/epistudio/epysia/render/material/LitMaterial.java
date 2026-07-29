package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.render.backend.TextureHandle;
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

    @Uniform
    public final Vector4f lightmapScaleOffset = new Vector4f(1.0f, 1.0f, 0.0f, 0.0f);

    @Uniform
    public float lightmapStrength = 1.0f;

    @Uniform
    public float lightmapRgbmRange = 0.0f;

    @Texture(colorSpace = ColorSpace.SRGB)
    public TextureHandle albedo;

    @Texture
    public TextureHandle normalMap;

    @Texture
    public TextureHandle metallicRoughnessMap;

    @Texture
    public TextureHandle occlusionMap;

    @Texture(colorSpace = ColorSpace.SRGB)
    public TextureHandle emissiveMap;

    @Texture
    public TextureHandle lightmap;

    private String surfaceShaderPath = "";
    private boolean animatedShadow = true;
    private boolean receiveShadows = true;

    public LitMaterial() {
        super("lit.vert.glsl", "lit.frag.glsl");
    }

    public boolean receiveShadows() {
        return receiveShadows;
    }

    public LitMaterial setReceiveShadows(boolean value) {
        this.receiveShadows = value;
        return this;
    }

    @Override
    public boolean alphaScissor() {
        return alphaCutoff > 0.0f;
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

    public LitMaterial setFloat(String uniformName, float value) {
        surfaceUniforms().setFloat(uniformName, value);
        return this;
    }

    public LitMaterial setInt(String uniformName, int value) {
        surfaceUniforms().setInt(uniformName, value);
        return this;
    }

    public LitMaterial setBool(String uniformName, boolean value) {
        surfaceUniforms().setBool(uniformName, value);
        return this;
    }

    public LitMaterial setVector2(String uniformName, Vector2f value) {
        surfaceUniforms().setVector2(uniformName, value);
        return this;
    }

    public LitMaterial setVector3(String uniformName, Vector3f value) {
        surfaceUniforms().setVector3(uniformName, value);
        return this;
    }

    public LitMaterial setVector4(String uniformName, Vector4f value) {
        surfaceUniforms().setVector4(uniformName, value);
        return this;
    }

    public LitMaterial setColor(String uniformName, Vector3f value) {
        surfaceUniforms().setVector3(uniformName, value);
        return this;
    }

    public LitMaterial setMatrix(String uniformName, Matrix4f value) {
        surfaceUniforms().setMatrix(uniformName, value);
        return this;
    }

    public LitMaterial setTexture(String uniformName, String path) {
        surfaceUniforms().setTexture(uniformName, path);
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

    public LitMaterial setLightmap(TextureHandle texture) {
        this.lightmap = texture;
        return this;
    }

    public LitMaterial setLightmapScaleOffset(float scaleU, float scaleV, float offsetU, float offsetV) {
        this.lightmapScaleOffset.set(scaleU, scaleV, offsetU, offsetV);
        return this;
    }

    public LitMaterial setLightmapStrength(float value) {
        this.lightmapStrength = value;
        return this;
    }

    public LitMaterial setLightmapRgbmRange(float value) {
        this.lightmapRgbmRange = value;
        return this;
    }

    public LitMaterial setEmissiveMap(TextureHandle texture) {
        this.emissiveMap = texture;
        return this;
    }
}
