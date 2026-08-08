package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.components.Export;

import java.util.Optional;

public final class BlendSample {
    @Export(label = "Clip", assetExtensions = {".epyclip"})
    private String clipPath = "";
    @Export(label = "Position X", min = -64.0f, max = 64.0f, step = 0.01f)
    private float positionX;
    @Export(label = "Position Y", min = -64.0f, max = 64.0f, step = 0.01f)
    private float positionY;

    private Optional<Clip> clip = Optional.empty();

    public String clipPath() {
        return clipPath;
    }

    public BlendSample setClipPath(String path) {
        clipPath = path == null ? "" : path;
        clip = Optional.empty();
        return this;
    }

    public BlendSample assignClip(String path, Clip resolvedClip) {
        clipPath = path;
        clip = Optional.of(resolvedClip);
        return this;
    }

    public Optional<Clip> resolvedClip() {
        return clip;
    }

    public float positionX() {
        return positionX;
    }

    public BlendSample setPositionX(float value) {
        positionX = value;
        return this;
    }

    public float positionY() {
        return positionY;
    }

    public BlendSample setPositionY(float value) {
        positionY = value;
        return this;
    }
}
