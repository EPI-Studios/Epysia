package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.exceptions.EpysiaException;

public record LayerDependency(GenerationLayer<?> layer, float padding) {

    public LayerDependency {
        if (padding < 0.0f) {
            throw new EpysiaException("Layer dependency padding must not be negative, got " + padding);
        }
    }

    public static LayerDependency of(GenerationLayer<?> layer, float padding) {
        return new LayerDependency(layer, padding);
    }
}
