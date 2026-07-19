package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epymesh.EpyMesh;
import fr.epistudio.epysia.assets.epymesh.EpyMeshFormat;
import fr.epistudio.epysia.assets.epymesh.EpyMeshSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.LoadedObj;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.ObjLoader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;

public final class MeshAssetLoader implements AssetLoader<UploadedMesh> {

    public static final String PRESET_PREFIX = "preset:";

    private final BuiltinMeshes builtinMeshes;

    public MeshAssetLoader(BuiltinMeshes builtinMeshes) {
        this.builtinMeshes = builtinMeshes;
    }

    @Override
    public Class<UploadedMesh> assetType() {
        return UploadedMesh.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".obj", ".gltf", ".glb", EpyMeshFormat.EXTENSION};
    }

    @Override
    public UploadedMesh load(EngineServices services, String path) {
        if (path.startsWith(PRESET_PREFIX)) {
            return builtinMeshes.get(path.substring(PRESET_PREFIX.length()));
        }
        if (path.endsWith(EpyMeshFormat.EXTENSION)) {
            EpyMesh decoded = EpyMeshSource.load(path);
            return MeshUploader.upload(services.renderBackend(), decoded.mesh());
        }
        if (path.endsWith(".obj")) {
            LoadedObj loaded = ObjLoader.load(services.renderBackend(), path);
            loaded.warnings().forEach(warning -> services.logger().warn("[MeshAssetLoader] " + warning));
            return loaded.mesh();
        }
        throw new EpysiaException("Unsupported mesh asset extension: " + path);
    }
}
