package fr.epistudio.epysia.render.backend;

public record RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode,
                          boolean depthWrite, boolean depthClamp, boolean colorWrite) {

    public RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode) {
        this(topology, depthTest, blendMode, cullMode, true, false, true);
    }

    public RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode, boolean depthWrite) {
        this(topology, depthTest, blendMode, cullMode, depthWrite, false, true);
    }

    public RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode,
                       boolean depthWrite, boolean depthClamp) {
        this(topology, depthTest, blendMode, cullMode, depthWrite, depthClamp, true);
    }

    public RenderState withDepthClamp() {
        return new RenderState(topology, depthTest, blendMode, cullMode, depthWrite, true, colorWrite);
    }

    public RenderState withoutColorWrite() {
        return new RenderState(topology, depthTest, blendMode, cullMode, depthWrite, depthClamp, false);
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
