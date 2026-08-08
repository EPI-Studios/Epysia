package fr.epistudio.epysia.render.backend;

public record DrawCommand(
        PipelineHandle pipeline,
        MeshHandle mesh,
        BindingSetHandle bindings,
        long sortKey,
        int instanceCount,
        int indexCountOverride,
        BufferHandle instanceBuffer,
        BufferHandle indirectBuffer,
        int indirectDrawCount,
        int firstIndexOverride,
        ScissorRect scissor
) {
    public static final int USE_MESH_INDEX_COUNT = -1;
    public static final int USE_MESH_FIRST_INDEX = -1;

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey,
                       int instanceCount, int indexCountOverride, BufferHandle instanceBuffer,
                       BufferHandle indirectBuffer, int indirectDrawCount) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, indexCountOverride,
                instanceBuffer, indirectBuffer, indirectDrawCount, USE_MESH_FIRST_INDEX, ScissorRect.disabled());
    }

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey,
                       int instanceCount, int indexCountOverride, BufferHandle instanceBuffer,
                       BufferHandle indirectBuffer) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, indexCountOverride,
                instanceBuffer, indirectBuffer, 1);
    }

    public static DrawCommand range(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings,
                                    long sortKey, int firstIndex, int indexCount, ScissorRect scissor) {
        return new DrawCommand(pipeline, mesh, bindings, sortKey, 1, indexCount,
                null, null, 1, firstIndex, scissor);
    }

    public boolean isMultiDraw() {
        return indirectBuffer != null && indirectDrawCount > 1;
    }

    public static DrawCommand multiDrawIndirect(PipelineHandle pipeline, MeshHandle arenaMesh,
                                                BindingSetHandle bindings, long sortKey,
                                                BufferHandle commands, int drawCount,
                                                BufferHandle perDrawBuffer) {
        return new DrawCommand(pipeline, arenaMesh, bindings, sortKey, 1, USE_MESH_INDEX_COUNT,
                perDrawBuffer, commands, drawCount);
    }

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey, int instanceCount, int indexCountOverride, BufferHandle instanceBuffer) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, indexCountOverride, instanceBuffer, null);
    }

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey, int instanceCount, int indexCountOverride) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, indexCountOverride, null, null);
    }

    public DrawCommand(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings, long sortKey, int instanceCount) {
        this(pipeline, mesh, bindings, sortKey, instanceCount, USE_MESH_INDEX_COUNT, null, null);
    }

    public static DrawCommand indirect(PipelineHandle pipeline, MeshHandle mesh, BindingSetHandle bindings,
                                       long sortKey, BufferHandle indirectBuffer) {
        return new DrawCommand(pipeline, mesh, bindings, sortKey, 1, USE_MESH_INDEX_COUNT, null, indirectBuffer);
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
