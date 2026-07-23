package fr.epistudio.epysia.render.backend;

public record RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode,
                          boolean depthWrite, boolean depthClamp) {

    public RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode) {
        this(topology, depthTest, blendMode, cullMode, true, false);
    }

    public RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode, boolean depthWrite) {
        this(topology, depthTest, blendMode, cullMode, depthWrite, false);
    }

    public RenderState withDepthClamp() {
        return new RenderState(topology, depthTest, blendMode, cullMode, depthWrite, true);
    }

    public static final RenderState OPAQUE_3D = new RenderState(
            Topology.TRIANGLES,
            DepthTest.LESS_EQUAL,
            BlendMode.OPAQUE,
            CullMode.BACK
    );

    public static final RenderState TRANSPARENT_3D = new RenderState(
            Topology.TRIANGLES,
            DepthTest.LESS_EQUAL,
            BlendMode.ALPHA_BLEND,
            CullMode.BACK,
            false
    );

    public static final RenderState SPRITE_2D = new RenderState(
            Topology.TRIANGLES,
            DepthTest.DISABLED,
            BlendMode.ALPHA_BLEND,
            CullMode.NONE
    );
}
