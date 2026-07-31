package fr.epistudio.epysia.render.baking;

import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.Optional;

public record ImpostorSurface(Optional<Path> albedoImage, Vector4f baseColor, float alphaCutoff, boolean opaque) {

    public ImpostorSurface {
        baseColor = new Vector4f(baseColor);
    }

    public static ImpostorSurface untextured() {
        return new ImpostorSurface(Optional.empty(), new Vector4f(1.0f), 0.0f, true);
    }
}
