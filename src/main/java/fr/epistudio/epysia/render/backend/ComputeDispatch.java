package fr.epistudio.epysia.render.backend;

public record ComputeDispatch(PipelineHandle pipeline, BindingSetHandle bindings,
                              int groupCountX, int groupCountY, int groupCountZ) {

    public static ComputeDispatch of(PipelineHandle pipeline, BindingSetHandle bindings, int groupCountX) {
        return new ComputeDispatch(pipeline, bindings, groupCountX, 1, 1);
    }
}
