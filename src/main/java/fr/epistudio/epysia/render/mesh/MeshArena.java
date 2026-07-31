package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class MeshArena {

    private static final IndexFormat INDEX_FORMAT = IndexFormat.UINT32;

    private final RenderBackend backend;
    private final int vertexStride;
    private final ArenaRange vertices;
    private final ArenaRange indices;
    private final BufferHandle vertexBuffer;
    private final BufferHandle indexBuffer;
    private final MeshHandle boundMesh;
    private final boolean skinned;
    private final boolean vertexColored;

    private MeshArena(RenderBackend backend, int vertexStride, int vertexCapacity, int indexCapacity,
                      boolean skinned, boolean vertexColored) {
        this.backend = backend;
        this.vertexStride = vertexStride;
        this.skinned = skinned;
        this.vertexColored = vertexColored;
        this.vertices = new ArenaRange(vertexCapacity);
        this.indices = new ArenaRange(indexCapacity);
        this.vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX,
                BufferUtils.createByteBuffer(vertexCapacity * vertexStride)));
        this.indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX,
                BufferUtils.createByteBuffer(indexCapacity * INDEX_FORMAT.byteSize())));
        this.boundMesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0, 0, INDEX_FORMAT));
    }

    public static MeshArena create(RenderBackend backend, int vertexCapacity, int indexCapacity,
                                   boolean skinned, boolean vertexColored) {
        int stride = MeshShaderBindings.vertexStride(skinned, vertexColored);
        return new MeshArena(backend, stride, vertexCapacity, indexCapacity, skinned, vertexColored);
    }

    public BufferHandle vertexBuffer() {
        return vertexBuffer;
    }

    public BufferHandle indexBuffer() {
        return indexBuffer;
    }

    public MeshHandle boundMesh() {
        return boundMesh;
    }

    public IndexFormat indexFormat() {
        return INDEX_FORMAT;
    }

    public int vertexCapacity() {
        return vertices.capacity();
    }

    public int indexCapacity() {
        return indices.capacity();
    }

    public int freeVertexCount() {
        return vertices.freeCount();
    }

    public int freeIndexCount() {
        return indices.freeCount();
    }

    public Optional<ArenaMesh> allocate(MeshData data) {
        requireCompatible(data);
        int vertexCount = data.vertexCount();
        int indexCount = data.indices().length;
        int vertexOffset = vertices.allocate(vertexCount);
        if (vertexOffset < 0) {
            return Optional.empty();
        }
        int indexOffset = indices.allocate(indexCount);
        if (indexOffset < 0) {
            vertices.release(vertexOffset, vertexCount);
            return Optional.empty();
        }
        ArenaMesh allocation = new ArenaMesh(vertexOffset, vertexCount, indexOffset, indexCount);
        write(allocation, data);
        return Optional.of(allocation);
    }

    private void requireCompatible(MeshData data) {
        if (data.hasSkin() != skinned || data.hasVertexColors() != vertexColored) {
            throw new EpysiaException("Mesh data layout does not match the arena layout: skinned="
                    + data.hasSkin() + " vertexColored=" + data.hasVertexColors());
        }
        if (data.hasLightmapUvs()) {
            throw new EpysiaException("An arena mesh cannot carry lightmap UVs.");
        }
    }

    private void write(ArenaMesh allocation, MeshData data) {
        ByteBuffer vertexBytes = MeshUploader.interleaveVertices(data);
        backend.writeBuffer(vertexBuffer, vertexBytes, (long) allocation.vertexOffset() * vertexStride);
        ByteBuffer indexBytes = MeshUploader.packIndices(
                offsetIndices(data.indices(), allocation.vertexOffset()), INDEX_FORMAT);
        backend.writeBuffer(indexBuffer, indexBytes,
                (long) allocation.indexOffset() * INDEX_FORMAT.byteSize());
    }

    private static int[] offsetIndices(int[] indices, int vertexOffset) {
        if (vertexOffset == 0) {
            return indices;
        }
        int[] offset = new int[indices.length];
        for (int index = 0; index < indices.length; index++) {
            offset[index] = indices[index] + vertexOffset;
        }
        return offset;
    }

    public MeshHandle createHandleFor(ArenaMesh allocation) {
        return backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer,
                allocation.indexOffset(), allocation.indexCount(), INDEX_FORMAT));
    }

    public void release(ArenaMesh allocation) {
        vertices.release(allocation.vertexOffset(), allocation.vertexCount());
        indices.release(allocation.indexOffset(), allocation.indexCount());
    }

    public void destroy() {
        backend.destroy(boundMesh);
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
    }
}
