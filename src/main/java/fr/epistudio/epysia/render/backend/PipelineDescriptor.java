package fr.epistudio.epysia.render.backend;

public record PipelineDescriptor(
        ShaderSource shaders,
        VertexLayout vertexLayout,
        RenderState state,
        BindingSetLayout bindingLayout,
        VertexLayout instanceLayout
) {

    public PipelineDescriptor(ShaderSource shaders, VertexLayout vertexLayout, RenderState state, BindingSetLayout bindingLayout) {
        this(shaders, vertexLayout, state, bindingLayout, null);
    }

    public boolean isInstanced() {
        return instanceLayout != null;
    }
}
