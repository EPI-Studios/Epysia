package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.ObjMesh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class MeshDataSource {

    private static final String CLASSPATH_ROOT = "models/";

    private MeshDataSource() {
    }

    static MeshData load(String path) {
        if (!path.endsWith(".obj")) {
            throw new EpysiaException("MeshCollider supports only .obj meshes for cooking, got: " + path);
        }
        AssetResolvers.ResolvedLocation location = AssetResolvers.forPath(path, CLASSPATH_ROOT);
        AssetSource source = location.source().orElseThrow(() ->
                new EpysiaException("Mesh not found on filesystem or classpath: " + path));
        return ObjMesh.parseFromSource(readText(source));
    }

    private static String readText(AssetSource source) {
        Optional<InputStream> opened = source.open();
        if (opened.isEmpty()) {
            throw new EpysiaException("Mesh not readable: " + source.path());
        }
        try (InputStream stream = opened.get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read " + source.path() + ": " + exception.getMessage());
        }
    }
}
