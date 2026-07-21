package fr.epistudio.epysia.render.mesh;

public final class MeshShaderBindings {

    public static final int FRAME_UBO_BINDING = 0;
    public static final int LIGHT_SSBO_BINDING = 0;
    public static final int CLUSTER_COUNT_SSBO_BINDING = 1;
    public static final int CLUSTER_INDEX_SSBO_BINDING = 2;
    public static final int OBJECT_UBO_BINDING = 1;
    public static final int INSTANCE_SSBO_BINDING = 3;
    public static final int JOINT_PALETTE_SSBO_BINDING = 4;
    public static final int MATERIAL_UBO_BINDING = 2;
    public static final int SHADOW_MAP_BINDING = 3;
    public static final int PICKING_UBO_BINDING = 2;
    public static final int CASCADE_UBO_BINDING = 2;
    public static final int SHADOW_MASK_MATERIAL_UBO_BINDING = 3;
    public static final int SHADOW_MASK_ALBEDO_BINDING = 0;
    public static final int IRRADIANCE_MAP_BINDING = 9;
    public static final int PREFILTERED_MAP_BINDING = 10;
    public static final int BRDF_LUT_BINDING = 11;
    public static final int SPOT_SHADOW_ATLAS_BINDING = 12;
    public static final int POINT_SHADOW_ATLAS_BINDING = 13;

    public static final int OBJECT_UBO_SIZE = 128;
    public static final int INSTANCE_TRANSFORM_BYTES = 128;
    public static final int MAX_CASCADES = 4;
    public static final int MAX_SHADOW_SPOTS = 8;
    public static final int MAX_SHADOW_POINTS = 4;
    public static final int POINT_SHADOW_FACES = 6;
    public static final int LIGHT_BYTES = 64;
    public static final int FRAME_HEADER_BYTES = 64 + MAX_CASCADES * 64 + 5 * 16;
    public static final int SPOT_SHADOW_COUNT_OFFSET = FRAME_HEADER_BYTES;
    public static final int SPOT_SHADOW_MATRICES_OFFSET = SPOT_SHADOW_COUNT_OFFSET + 16;
    public static final int POINT_SHADOW_COUNT_OFFSET = SPOT_SHADOW_MATRICES_OFFSET + MAX_SHADOW_SPOTS * 64;
    public static final int POINT_SHADOW_MATRICES_OFFSET = POINT_SHADOW_COUNT_OFFSET + 16;

    public static final int CLUSTER_X = Integer.getInteger("epysia.cluster.x", 16);
    public static final int CLUSTER_Y = Integer.getInteger("epysia.cluster.y", 9);
    public static final int CLUSTER_Z = Integer.getInteger("epysia.cluster.z", 24);
    public static final int CLUSTER_COUNT = CLUSTER_X * CLUSTER_Y * CLUSTER_Z;
    public static final int MAX_LIGHTS_PER_CLUSTER = 32;
    public static final int CLUSTER_GRID_OFFSET = POINT_SHADOW_MATRICES_OFFSET + MAX_SHADOW_POINTS * POINT_SHADOW_FACES * 64;
    public static final int CLUSTER_PARAMS_OFFSET = CLUSTER_GRID_OFFSET + 16;
    public static final int CLUSTER_SLICE_OFFSET = CLUSTER_PARAMS_OFFSET + 16;
    public static final int FRAME_UBO_SIZE = CLUSTER_SLICE_OFFSET + 16;
    public static final int PICKING_UBO_SIZE = 16;
    public static final int CASCADE_UBO_SIZE = 16;

    public static final int VERTEX_STRIDE = MeshData.VERTEX_FLOAT_COUNT * Float.BYTES;
    public static final int SKINNED_VERTEX_STRIDE = VERTEX_STRIDE + 24;

    private MeshShaderBindings() {
    }
}
