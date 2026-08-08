package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.List;

final class DeformedMesh {
    private final BufferHandle vertexBuffer;
    private final BufferHandle parameterBuffer;
    private final BindingSetHandle bindings;
    private final List<UploadedSubmesh> submeshes;
    private final int vertexCount;
    private boolean stale = true;

    DeformedMesh(BufferHandle vertexBuffer, BufferHandle parameterBuffer, BindingSetHandle bindings,
                 List<UploadedSubmesh> submeshes, int vertexCount) {
        this.vertexBuffer = vertexBuffer;
        this.parameterBuffer = parameterBuffer;
        this.bindings = bindings;
        this.submeshes = List.copyOf(submeshes);
        this.vertexCount = vertexCount;
    }

    BindingSetHandle bindings() {
        return bindings;
    }

    BufferHandle vertexBuffer() {
        return vertexBuffer;
    }

    UploadedSubmesh submesh(int slot) {
        return submeshes.get(slot);
    }

    int vertexCount() {
        return vertexCount;
    }

    void markStale() {
        stale = true;
    }

    boolean consumeStale() {
        if (!stale) {
            return false;
        }
        stale = false;
        return true;
    }

    void destroy(RenderBackend backend) {
        for (UploadedSubmesh submesh : submeshes) {
            backend.destroy(submesh.handle());
        }
        backend.destroy(bindings);
        backend.destroy(parameterBuffer);
        backend.destroy(vertexBuffer);
    }
}
