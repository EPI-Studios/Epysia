package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineHandle;

import java.nio.ByteBuffer;

final class UiQuadBatch {

    static final int VERTICES_PER_QUAD = 4;
    static final int INDICES_PER_QUAD = 6;

    final PipelineHandle pipeline;
    final BindingSetHandle bindings;
    final BufferHandle vertexBuffer;
    final BufferHandle indexBuffer;
    final MeshHandle mesh;
    final ByteBuffer vertexScratch;
    final ByteBuffer indexScratch;
    final int vertexBytes;
    final int maxQuads;
    int quadCount;

    UiQuadBatch(PipelineHandle pipeline, BindingSetHandle bindings, BufferHandle vertexBuffer,
                BufferHandle indexBuffer, MeshHandle mesh, ByteBuffer vertexScratch,
                ByteBuffer indexScratch, int vertexBytes, int maxQuads) {
        this.pipeline = pipeline;
        this.bindings = bindings;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.mesh = mesh;
        this.vertexScratch = vertexScratch;
        this.indexScratch = indexScratch;
        this.vertexBytes = vertexBytes;
        this.maxQuads = maxQuads;
    }

    void reset() {
        vertexScratch.clear();
        indexScratch.clear();
        quadCount = 0;
    }

    boolean isFull() {
        return quadCount >= maxQuads;
    }

    int indexCount() {
        return quadCount * INDICES_PER_QUAD;
    }
}
