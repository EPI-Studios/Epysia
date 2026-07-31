package fr.epistudio.epysia.assets.epyimpostor;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.QuadMesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ImpostorRenderable(MeshData quad, LitMaterial material) {

    public static final String SURFACE_SHADER_PATH = "impostor.surf.glsl";
    public static final String ALBEDO_ATLAS_UNIFORM = "impostorAlbedoAtlas";
    public static final String NORMAL_ATLAS_UNIFORM = "impostorNormalAtlas";
    public static final String GRID_SIZE_UNIFORM = "impostorGridSize";
    public static final String RADIUS_UNIFORM = "impostorRadius";
    public static final String CENTER_UNIFORM = "impostorCenter";
    public static final String COVERAGE_CUTOFF_UNIFORM = "impostorCoverageCutoff";
    public static final float DEFAULT_COVERAGE_CUTOFF = 0.35f;

    private static final float QUAD_HALF_SIZE = 0.5f;
    private static final int QUAD_SEGMENTS = 1;

    public static ImpostorRenderable read(Path descriptorFile) {
        return read(descriptorFile, DEFAULT_COVERAGE_CUTOFF);
    }

    public static ImpostorRenderable read(Path descriptorFile, float coverageCutoff) {
        ImpostorAtlas atlas = new ImpostorAtlasJsonCodec().read(readText(descriptorFile));
        return of(atlas, siblingOf(descriptorFile, atlas.albedoAtlasPath()),
                siblingOf(descriptorFile, atlas.normalAtlasPath()), coverageCutoff);
    }

    public static ImpostorRenderable of(ImpostorAtlas atlas, String albedoAtlasPath, String normalAtlasPath,
                                        float coverageCutoff) {
        return new ImpostorRenderable(QuadMesh.data(QUAD_HALF_SIZE, QUAD_SEGMENTS),
                materialFor(atlas, albedoAtlasPath, normalAtlasPath, coverageCutoff));
    }

    private static LitMaterial materialFor(ImpostorAtlas atlas, String albedoAtlasPath, String normalAtlasPath,
                                           float coverageCutoff) {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath(SURFACE_SHADER_PATH)
                .setTexture(ALBEDO_ATLAS_UNIFORM, albedoAtlasPath)
                .setTexture(NORMAL_ATLAS_UNIFORM, normalAtlasPath)
                .setInt(GRID_SIZE_UNIFORM, atlas.gridSize())
                .setFloat(RADIUS_UNIFORM, atlas.radius())
                .setVector3(CENTER_UNIFORM, atlas.center())
                .setFloat(COVERAGE_CUTOFF_UNIFORM, coverageCutoff)
                .setAlphaCutoff(coverageCutoff);
        return material;
    }

    private static String siblingOf(Path descriptorFile, String fileName) {
        return descriptorFile.toAbsolutePath().resolveSibling(fileName).toString();
    }

    private static String readText(Path descriptorFile) {
        try {
            return Files.readString(descriptorFile);
        } catch (IOException failure) {
            throw new EpysiaException("Failed to read " + descriptorFile + ": " + failure.getMessage(), failure);
        }
    }
}
