package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BuiltinMeshes {
    public static final String CUBE = "cube";
    public static final String PLANE = "plane";
    public static final String CAPSULE = "capsule";
    public static final String SPHERE = "sphere";
    public static final String UNIT_QUAD = "unitQuad";
    public static final String QUAD = "quad";

    public static final String PRESET_PREFIX = "preset:";
    public static final char SEGMENT_SEPARATOR = ':';
    public static final int MAXIMUM_SEGMENTS = 256;

    private static final float UNIT_QUAD_HALF_SIZE = 0.5f;
    private static final float UNIT_QUAD_UV_TILES = 1.0f;
    public static final float PLANE_HALF_SIZE = 20.0f;
    private static final float PLANE_UV_TILES = 20.0f;
    private static final float CUBE_HALF_SIZE = 0.5f;
    private static final int PLANE_DEFAULT_SEGMENTS = 64;

    private final Map<String, UploadedMesh> meshes = new LinkedHashMap<>();
    private RenderBackend backend;

    private BuiltinMeshes() {
    }

    public static BuiltinMeshes uploadAll(RenderBackend backend) {
        BuiltinMeshes library = new BuiltinMeshes();
        library.backend = backend;
        library.meshes.put(CUBE, MeshUploader.upload(backend, CubeMesh.data()));
        library.meshes.put(PLANE, MeshUploader.upload(backend,
                PlaneMesh.data(PLANE_HALF_SIZE, PLANE_UV_TILES, PLANE_DEFAULT_SEGMENTS)));
        library.meshes.put(CAPSULE, MeshUploader.upload(backend, CapsuleMesh.data()));
        library.meshes.put(SPHERE, MeshUploader.upload(backend, SphereMesh.data()));
        library.meshes.put(UNIT_QUAD, MeshUploader.upload(backend,
                PlaneMesh.data(UNIT_QUAD_HALF_SIZE, UNIT_QUAD_UV_TILES)));
        library.meshes.put(QUAD, MeshUploader.upload(backend, QuadMesh.data(UNIT_QUAD_HALF_SIZE, 1)));
        return library;
    }

    public UploadedMesh get(String preset) {
        if (preset == null || preset.isEmpty()) {
            return null;
        }
        UploadedMesh existing = meshes.get(preset);
        if (existing != null) {
            return existing;
        }
        return subdivided(preset).orElse(null);
    }

    private Optional<UploadedMesh> subdivided(String preset) {
        if (preset.indexOf(SEGMENT_SEPARATOR) <= 0 || backend == null) {
            return Optional.empty();
        }
        return presetData(preset).map(data -> cache(preset, data));
    }

    private UploadedMesh cache(String preset, MeshData data) {
        UploadedMesh uploaded = MeshUploader.upload(backend, data);
        meshes.put(preset, uploaded);
        return uploaded;
    }

    private static Optional<Integer> parseSegments(String text) {
        try {
            int segments = Integer.parseInt(text.trim());
            return segments >= 1 ? Optional.of(Math.min(segments, MAXIMUM_SEGMENTS)) : Optional.empty();
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    public static Optional<MeshData> presetData(String presetName) {
        String[] tokens = presetName.split(String.valueOf(SEGMENT_SEPARATOR));
        String name = tokens[0];
        if (tokens.length == 1) {
            return build(name, defaultSegmentsFor(name));
        }
        Optional<Integer> width = parseSegments(tokens[1]);
        if (width.isEmpty()) {
            return Optional.empty();
        }
        if (tokens.length == 2) {
            return build(name, width.get());
        }
        Optional<Integer> height = parseSegments(tokens[2]);
        return height.isEmpty()
                ? Optional.empty()
                : buildRectangular(name, width.get(), height.get());
    }

    private static Optional<MeshData> buildRectangular(String name, int widthSegments, int heightSegments) {
        if (QUAD.equals(name)) {
            return Optional.of(QuadMesh.data(UNIT_QUAD_HALF_SIZE, widthSegments, heightSegments));
        }
        return build(name, widthSegments);
    }

    private static int defaultSegmentsFor(String presetName) {
        return PLANE.equals(presetName) ? PLANE_DEFAULT_SEGMENTS : 1;
    }

    private static Optional<MeshData> build(String name, int segments) {
        return switch (name) {
            case PLANE -> Optional.of(PlaneMesh.data(PLANE_HALF_SIZE, PLANE_UV_TILES, segments));
            case UNIT_QUAD -> Optional.of(PlaneMesh.data(UNIT_QUAD_HALF_SIZE, UNIT_QUAD_UV_TILES, segments));
            case CUBE -> Optional.of(CubeMeshGrid.data(CUBE_HALF_SIZE, segments));
            case SPHERE -> Optional.of(SphereMesh.data(UNIT_QUAD_HALF_SIZE, segments, segments * 2));
            case QUAD -> Optional.of(QuadMesh.data(UNIT_QUAD_HALF_SIZE, segments));
            default -> Optional.empty();
        };
    }

    public boolean has(String preset) {
        return get(preset) != null;
    }

    public boolean contains(UploadedMesh mesh) {
        return meshes.containsValue(mesh);
    }
}
