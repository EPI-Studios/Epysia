package fr.epistudio.epysia.render.backend;

public record MeshDescriptor(
        BufferHandle vertexBuffer,
        BufferHandle indexBuffer,
        int firstIndex,
        int indexCount,
        IndexFormat indexFormat
) {
}
