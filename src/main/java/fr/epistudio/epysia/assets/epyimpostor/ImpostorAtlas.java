package fr.epistudio.epysia.assets.epyimpostor;

import org.joml.Vector3f;

public record ImpostorAtlas(String mapping, int gridSize, int tileSize, float radius, Vector3f center,
                            String albedoAtlasPath, String normalAtlasPath) {

    public ImpostorAtlas {
        center = new Vector3f(center);
    }

    public static ImpostorAtlas hemiOctahedral(int gridSize, int tileSize, float radius, Vector3f center,
                                               String albedoAtlasPath, String normalAtlasPath) {
        return new ImpostorAtlas(EpyImpostorFormat.HEMI_OCTAHEDRAL_MAPPING, gridSize, tileSize, radius, center,
                albedoAtlasPath, normalAtlasPath);
    }

    public int atlasSize() {
        return gridSize * tileSize;
    }

    public float depthRange() {
        return radius * 2.0f;
    }
}
