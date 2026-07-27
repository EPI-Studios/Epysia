package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.render.backend.TextureHandle;

import java.util.Objects;
import java.util.Optional;

public record SkySource(SkyMode mode, String shaderPath, Optional<TextureHandle> texture) {

    private static final String BUILTIN_BODY_PATH = "lib/sky.glsl";
    private static final String EQUIRECT_BODY_PATH = "lib/sky_equirect.glsl";

    public static final SkySource PROCEDURAL = new SkySource(SkyMode.PROCEDURAL, "", Optional.empty());

    public static SkySource ofShader(String shaderPath) {
        return shaderPath.isBlank()
                ? PROCEDURAL
                : new SkySource(SkyMode.SHADER, shaderPath, Optional.empty());
    }

    public static SkySource ofTexture(TextureHandle texture) {
        return new SkySource(SkyMode.TEXTURE, "", Optional.of(texture));
    }

    public String bodyPath() {
        return switch (mode) {
            case PROCEDURAL -> BUILTIN_BODY_PATH;
            case SHADER -> shaderPath;
            case TEXTURE -> EQUIRECT_BODY_PATH;
        };
    }

    public boolean needsTexture() {
        return mode == SkyMode.TEXTURE;
    }

    public boolean sameAs(SkySource other) {
        return mode == other.mode
                && shaderPath.equals(other.shaderPath)
                && sameTexture(other);
    }

    private boolean sameTexture(SkySource other) {
        return Objects.equals(texture.map(TextureHandle::id), other.texture.map(TextureHandle::id));
    }
}
