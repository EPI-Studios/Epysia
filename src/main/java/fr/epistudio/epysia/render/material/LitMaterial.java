package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector3f;

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

    @Texture
    public TextureHandle albedo;

    @Texture
    public TextureHandle normalMap;

    @Texture
    public TextureHandle metallicRoughnessMap;

    @Texture
    public TextureHandle occlusionMap;

    @Texture
    public TextureHandle emissiveMap;

    public LitMaterial() {
        super("lit.vert.glsl", "lit.frag.glsl");
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
