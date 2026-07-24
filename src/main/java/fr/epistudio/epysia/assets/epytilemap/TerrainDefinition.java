package fr.epistudio.epysia.assets.epytilemap;

import org.joml.Vector4f;

public record TerrainDefinition(String name, Vector4f color) {

    public static TerrainDefinition named(String name) {
        return new TerrainDefinition(name, new Vector4f(0.4f, 0.8f, 0.4f, 1.0f));
    }
}
