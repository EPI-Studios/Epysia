package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.List;

public record UploadedMesh(
        BufferHandle vertexBuffer,
        BufferHandle indexBuffer,
        List<UploadedSubmesh> submeshes,
        Aabb localBounds
) {

    public UploadedMesh {
        submeshes = List.copyOf(submeshes);
    }

    public void destroy(RenderBackend backend) {
        for (UploadedSubmesh submesh : submeshes) {
            backend.destroy(submesh.handle());
        }
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
    }
}
