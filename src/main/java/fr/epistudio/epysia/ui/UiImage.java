package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.TextureHandle;

public final class UiImage extends UiNode {

    private TextureHandle texture;
    private UiColor tint = UiColor.WHITE;
    private float uvMinX = 0.0f;
    private float uvMinY = 0.0f;
    private float uvMaxX = 1.0f;
    private float uvMaxY = 1.0f;

    public UiImage setTexture(TextureHandle texture) {
        this.texture = texture;
        return this;
    }

    public UiImage setTint(UiColor tint) {
        this.tint = tint;
        return this;
    }

    public UiImage setUvRegion(float minX, float minY, float maxX, float maxY) {
        this.uvMinX = minX;
        this.uvMinY = minY;
        this.uvMaxX = maxX;
        this.uvMaxY = maxY;
        return this;
    }

    public TextureHandle texture() {
        return texture;
    }

    public UiColor tint() {
        return tint;
    }

    public float uvMinX() {
        return uvMinX;
    }

    public float uvMinY() {
        return uvMinY;
    }

    public float uvMaxX() {
        return uvMaxX;
    }

    public float uvMaxY() {
        return uvMaxY;
    }
}
