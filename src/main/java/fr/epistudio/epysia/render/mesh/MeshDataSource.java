package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Locale;

public final class MeshDataSource {

    private static final String CLASSPATH_ROOT = "models/";
    private static final String OBJ_EXTENSION = ".obj";

    private MeshDataSource() {
    }

    public static Optional<MeshData> load(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        if (path.startsWith(BuiltinMeshes.PRESET_PREFIX)) {
            return BuiltinMeshes.presetData(path.substring(BuiltinMeshes.PRESET_PREFIX.length()));
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(OBJ_EXTENSION)) {
            return readText(path).map(ObjMesh::parseFromSource);
        }
        return Optional.empty();
    }

    private static Optional<String> readText(String path) {
        Optional<AssetSource> source = AssetResolvers.forPath(path, CLASSPATH_ROOT).source();
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Optional<InputStream> opened = source.get().open();
        if (opened.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = opened.get()) {
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            return Optional.empty();
        }
    }
}
