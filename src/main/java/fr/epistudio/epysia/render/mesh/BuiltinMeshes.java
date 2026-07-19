package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BuiltinMeshes {

    public static final String CUBE = "cube";
    public static final String PLANE = "plane";
    public static final String CAPSULE = "capsule";

    private final Map<String, UploadedMesh> meshes = new LinkedHashMap<>();

    private BuiltinMeshes() {
    }

    public static BuiltinMeshes uploadAll(RenderBackend backend) {
        BuiltinMeshes library = new BuiltinMeshes();
        library.meshes.put(CUBE, MeshUploader.upload(backend, CubeMesh.data()));
        library.meshes.put(PLANE, MeshUploader.upload(backend, PlaneMesh.data(20.0f, 20.0f)));
        library.meshes.put(CAPSULE, MeshUploader.upload(backend, CapsuleMesh.data()));
        return library;
    }

    public UploadedMesh get(String preset) {
        if (preset == null || preset.isEmpty()) {
            return null;
        }
        return meshes.get(preset);
    }

    public boolean has(String preset) {
        return preset != null && meshes.containsKey(preset);
    }
}
