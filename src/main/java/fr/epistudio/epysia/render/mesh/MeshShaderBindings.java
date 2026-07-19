package fr.epistudio.epysia.render.mesh;

public final class MeshShaderBindings {

    public static final int FRAME_UBO_BINDING = 0;
    public static final int OBJECT_UBO_BINDING = 1;
    public static final int MATERIAL_UBO_BINDING = 2;
    public static final int SHADOW_MAP_BINDING = 3;
    public static final int PICKING_UBO_BINDING = 2;
    public static final int CASCADE_UBO_BINDING = 2;
    public static final int SHADOW_MASK_MATERIAL_UBO_BINDING = 3;
    public static final int SHADOW_MASK_ALBEDO_BINDING = 0;
    public static final int IRRADIANCE_MAP_BINDING = 9;
    public static final int PREFILTERED_MAP_BINDING = 10;
    public static final int BRDF_LUT_BINDING = 11;

    public static final int OBJECT_UBO_SIZE = 128;
    public static final int MAX_LIGHTS = 8;
    public static final int MAX_CASCADES = 4;
    public static final int LIGHT_BYTES = 64;
    public static final int FRAME_HEADER_BYTES = 64 + MAX_CASCADES * 64 + 5 * 16;
    public static final int FRAME_UBO_SIZE = FRAME_HEADER_BYTES + MAX_LIGHTS * LIGHT_BYTES;
    public static final int PICKING_UBO_SIZE = 16;
    public static final int CASCADE_UBO_SIZE = 16;

    public static final int VERTEX_STRIDE = MeshData.VERTEX_FLOAT_COUNT * Float.BYTES;

    private MeshShaderBindings() {
    }
}
