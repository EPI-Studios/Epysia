package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BuiltinMeshes {

    public static final String CUBE = "cube";
    public static final String PLANE = "plane";
    public static final String CAPSULE = "capsule";
    public static final String SPHERE = "sphere";
    public static final String UNIT_QUAD = "unitQuad";

    private static final float UNIT_QUAD_HALF_SIZE = 0.5f;
    private static final float UNIT_QUAD_UV_TILES = 1.0f;

    private final Map<String, UploadedMesh> meshes = new LinkedHashMap<>();

    private BuiltinMeshes() {
    }

    public static BuiltinMeshes uploadAll(RenderBackend backend) {
        BuiltinMeshes library = new BuiltinMeshes();
        library.meshes.put(CUBE, MeshUploader.upload(backend, CubeMesh.data()));
        library.meshes.put(PLANE, MeshUploader.upload(backend, PlaneMesh.data(20.0f, 20.0f)));
        library.meshes.put(CAPSULE, MeshUploader.upload(backend, CapsuleMesh.data()));
        library.meshes.put(SPHERE, MeshUploader.upload(backend, SphereMesh.data()));
        library.meshes.put(UNIT_QUAD, MeshUploader.upload(backend,
                PlaneMesh.data(UNIT_QUAD_HALF_SIZE, UNIT_QUAD_UV_TILES)));
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
