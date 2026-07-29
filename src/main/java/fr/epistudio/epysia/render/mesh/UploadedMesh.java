package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.List;
import java.util.Optional;

public record UploadedMesh(
        BufferHandle vertexBuffer,
        BufferHandle indexBuffer,
        List<UploadedSubmesh> submeshes,
        Aabb localBounds,
        boolean skinned,
        boolean vertexColored,
        Optional<Skeleton> skeleton,
        Optional<StorageBufferBinding> lightmapUvs
) {

    public UploadedMesh {
        if (skinned && skeleton.isEmpty()) {
            throw new EpysiaException("Skinned mesh requires a skeleton.");
        }
        submeshes = List.copyOf(submeshes);
    }

    public UploadedMesh(BufferHandle vertexBuffer, BufferHandle indexBuffer, List<UploadedSubmesh> submeshes,
                        Aabb localBounds, boolean skinned, boolean vertexColored, Optional<Skeleton> skeleton) {
        this(vertexBuffer, indexBuffer, submeshes, localBounds, skinned, vertexColored, skeleton, Optional.empty());
    }

    public boolean lightmapped() {
        return lightmapUvs.isPresent();
    }

    public void destroy(RenderBackend backend) {
        for (UploadedSubmesh submesh : submeshes) {
            backend.destroy(submesh.handle());
        }
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        lightmapUvs.ifPresent(binding -> backend.destroy(binding.buffer()));
    }
}
