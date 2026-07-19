package fr.epistudio.epysia.render.backend;

public record DrawCommand(
        PipelineHandle pipeline,
        MeshHandle mesh,
        BindingSetHandle bindings,
        long sortKey,
        int instanceCount,
        int indexCountOverride,
        BufferHandle instanceBuffer
) {

    public static final int USE_MESH_INDEX_COUNT = -1;

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey, int instanceCount, int indexCountOverride) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, indexCountOverride, null);
    }

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey, int instanceCount) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, USE_MESH_INDEX_COUNT, null);
    }

    public static DrawCommand of(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey) {
        return new DrawCommand(pipeline, mesh, bindings, sortKey, 1, USE_MESH_INDEX_COUNT, null);
    }

    public static DrawCommand of(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings) {
        return new DrawCommand(pipeline, mesh, bindings, 0L, 1, USE_MESH_INDEX_COUNT, null);
    }

    public static DrawCommand withIndexCount(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, int indexCount) {
        return new DrawCommand(pipeline, mesh, bindings, 0L, 1, indexCount, null);
    }

    public static DrawCommand instanced(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, BufferHandle instanceBuffer, int instanceCount) {
        return new DrawCommand(pipeline, mesh, bindings, 0L, instanceCount, USE_MESH_INDEX_COUNT, instanceBuffer);
    }
}
