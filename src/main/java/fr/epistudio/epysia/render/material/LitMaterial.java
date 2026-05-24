package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector3f;

public final class LitMaterial extends Material {

    @Uniform
    public final Vector3f baseColor = new Vector3f(1.0f, 1.0f, 1.0f);

    @Uniform
    public float shininess = 32.0f;

    @Uniform
    public float specularStrength = 0.5f;

    @Texture
    public TextureHandle albedo;

    @Texture
    public TextureHandle normalMap;

    public LitMaterial() {
        super("lit.vert.glsl", "lit.frag.glsl");
    }

    public LitMaterial setBaseColor(float red, float green, float blue) {
        baseColor.set(red, green, blue);
        return this;
    }

    public LitMaterial setShininess(float value) {
        this.shininess = value;
        return this;
    }

    public LitMaterial setSpecularStrength(float value) {
        this.specularStrength = value;
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
}
