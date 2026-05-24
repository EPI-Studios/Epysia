package fr.epistudio.epysia.render.backend;

public record RenderState(Topology topology, DepthTest depthTest, BlendMode blendMode, CullMode cullMode) {

    public static final RenderState OPAQUE_3D = new RenderState(
            Topology.TRIANGLES,
            DepthTest.LESS_EQUAL,
            BlendMode.OPAQUE,
            CullMode.BACK
    );

    public static final RenderState SPRITE_2D = new RenderState(
            Topology.TRIANGLES,
            DepthTest.DISABLED,
            BlendMode.ALPHA_BLEND,
            CullMode.NONE
    );
}
