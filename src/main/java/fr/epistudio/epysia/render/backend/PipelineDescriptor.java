package fr.epistudio.epysia.render.backend;

public record PipelineDescriptor(
        ShaderSource shaders,
        VertexLayout vertexLayout,
        RenderState state,
        BindingSetLayout bindingLayout
) {
}
