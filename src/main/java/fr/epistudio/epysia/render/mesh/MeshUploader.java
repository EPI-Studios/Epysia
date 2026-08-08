package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MeshUploader {
    private static final int MAX_SHORT_INDEXED_VERTICES = 65536;

    private MeshUploader() {
    }

    public static UploadedMesh upload(RenderBackend backend, MeshData data) {
        return upload(backend, data, Optional.empty());
    }

    public static Optional<UploadedMesh> uploadInto(RenderBackend backend, MeshData data, MeshArena arena) {
        Optional<ArenaMesh> allocation = arena.allocate(data);
        if (allocation.isEmpty()) {
            return Optional.empty();
        }
        ArenaMesh placed = allocation.get();
        List<UploadedSubmesh> uploadedSubmeshes = new ArrayList<>(data.submeshes().size());
        for (Submesh submesh : data.submeshes()) {
            uploadedSubmeshes.add(new UploadedSubmesh(arena.createHandleFor(
                    new ArenaMesh(placed.vertexOffset(), placed.vertexCount(),
                            placed.indexOffset() + submesh.indexOffset(), submesh.indexCount())),
                    submesh.materialSlot()));
        }
        return Optional.of(new UploadedMesh(
                arena.vertexBuffer(),
                arena.indexBuffer(),
                uploadedSubmeshes,
                data.vertexCount(),
                Aabb.fromPositions(data.positions()),
                data.hasSkin(),
                data.hasVertexColors(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ArenaPlacement(arena, placed))));
    }

    public static ResizableMesh uploadResizable(RenderBackend backend, MeshData data,
                                                int maximumVertexCount, int maximumIndexCount) {
        IndexFormat indexFormat = IndexFormat.UINT32;
        ByteBuffer vertices = interleaveVertices(data);
        ByteBuffer indices = packIndices(data.indices(), indexFormat);
        int vertexStride = MeshShaderBindings.vertexStride(data.hasSkin(), data.hasVertexColors());
        int vertexCapacity = Math.max(vertices.remaining(), maximumVertexCount * vertexStride);
        int indexCapacity = Math.max(indices.remaining(), maximumIndexCount * indexFormat.byteSize());
        BufferHandle vertexBuffer = backend.createBuffer(
                new BufferDescriptor(BufferUsage.VERTEX, padded(vertices, vertexCapacity)));
        BufferHandle indexBuffer = backend.createBuffer(
                new BufferDescriptor(BufferUsage.INDEX, padded(indices, indexCapacity)));
        UploadedMesh mesh = assemble(data, vertexBuffer, indexBuffer,
                submeshHandles(backend, data, vertexBuffer, indexBuffer, indexFormat),
                Optional.empty(), Optional.empty());
        return new ResizableMesh(mesh, indexFormat, vertexCapacity, indexCapacity);
    }

    static ByteBuffer padded(ByteBuffer source, int capacityBytes) {
        if (source.remaining() == capacityBytes) {
            return source;
        }
        ByteBuffer padded = BufferUtils.createByteBuffer(capacityBytes);
        padded.put(source.duplicate());
        padded.position(0);
        padded.limit(capacityBytes);
        return padded;
    }

    public static UploadedMesh upload(RenderBackend backend, MeshData data, Optional<Skeleton> skeleton) {
        IndexFormat indexFormat = indexFormatFor(data);
        BufferHandle vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, interleaveVertices(data)));
        BufferHandle indexBuffer = backend.createBuffer(
                new BufferDescriptor(BufferUsage.INDEX, packIndices(data.indices(), indexFormat)));
        return assemble(data, vertexBuffer, indexBuffer,
                submeshHandles(backend, data, vertexBuffer, indexBuffer, indexFormat),
                skeleton, lightmapUvBuffer(backend, data));
    }

    private static List<UploadedSubmesh> submeshHandles(RenderBackend backend, MeshData data,
                                                        BufferHandle vertexBuffer, BufferHandle indexBuffer,
                                                        IndexFormat indexFormat) {
        List<UploadedSubmesh> uploadedSubmeshes = new ArrayList<>(data.submeshes().size());
        for (Submesh submesh : data.submeshes()) {
            MeshHandle handle = backend.createMesh(new MeshDescriptor(
                    vertexBuffer, indexBuffer, submesh.indexOffset(), submesh.indexCount(), indexFormat));
            uploadedSubmeshes.add(new UploadedSubmesh(handle, submesh.materialSlot()));
        }
        return uploadedSubmeshes;
    }

    private static UploadedMesh assemble(MeshData data, BufferHandle vertexBuffer, BufferHandle indexBuffer,
                                         List<UploadedSubmesh> uploadedSubmeshes, Optional<Skeleton> skeleton,
                                         Optional<StorageBufferBinding> lightmapUvs) {
        return new UploadedMesh(
                vertexBuffer,
                indexBuffer,
                uploadedSubmeshes,
                data.vertexCount(),
                Aabb.fromPositions(data.positions()),
                data.hasSkin(),
                data.hasVertexColors(),
                skeleton,
                lightmapUvs
        );
    }

    private static Optional<StorageBufferBinding> lightmapUvBuffer(RenderBackend backend, MeshData data) {
        if (!data.hasLightmapUvs()) {
            return Optional.empty();
        }
        int byteSize = data.lightmapUvs().length * Float.BYTES;
        ByteBuffer bytes = BufferUtils.createByteBuffer(byteSize);
        bytes.asFloatBuffer().put(data.lightmapUvs());
        return Optional.of(StorageBufferBinding.whole(
                backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, bytes)), byteSize));
    }

    static ByteBuffer interleaveVertices(MeshData data) {
        int vertexCount = data.vertexCount();
        boolean hasUvs = data.uvs().length > 0;
        boolean hasTangents = data.tangents().length > 0;
        boolean skinned = data.hasSkin();
        boolean colored = data.hasVertexColors();
        int stride = MeshShaderBindings.vertexStride(skinned, colored);
        ByteBuffer bytes = BufferUtils.createByteBuffer(vertexCount * stride);
        for (int i = 0; i < vertexCount; i++) {
            int positionBase = i * MeshData.POSITION_COMPONENTS;
            int normalBase = i * MeshData.NORMAL_COMPONENTS;
            int uvBase = i * MeshData.UV_COMPONENTS;
            int tangentBase = i * MeshData.TANGENT_COMPONENTS;
            bytes.putFloat(data.positions()[positionBase]);
            bytes.putFloat(data.positions()[positionBase + 1]);
            bytes.putFloat(data.positions()[positionBase + 2]);
            bytes.putFloat(data.normals()[normalBase]);
            bytes.putFloat(data.normals()[normalBase + 1]);
            bytes.putFloat(data.normals()[normalBase + 2]);
            bytes.putFloat(hasUvs ? data.uvs()[uvBase] : 0.0f);
            bytes.putFloat(hasUvs ? data.uvs()[uvBase + 1] : 0.0f);
            bytes.putFloat(hasTangents ? data.tangents()[tangentBase] : 1.0f);
            bytes.putFloat(hasTangents ? data.tangents()[tangentBase + 1] : 0.0f);
            bytes.putFloat(hasTangents ? data.tangents()[tangentBase + 2] : 0.0f);
            if (colored) {
                appendVertexColor(bytes, data, i);
            }
            if (skinned) {
                appendSkinInfluences(bytes, data, i);
            }
        }
        bytes.flip();
        return bytes;
    }

    private static void appendVertexColor(ByteBuffer bytes, MeshData data, int vertexIndex) {
        int colorBase = vertexIndex * MeshData.COLOR_COMPONENTS;
        for (int component = 0; component < MeshData.COLOR_COMPONENTS; component++) {
            bytes.putFloat(data.vertexColors()[colorBase + component]);
        }
    }

    private static void appendSkinInfluences(ByteBuffer bytes, MeshData data, int vertexIndex) {
        int skinBase = vertexIndex * MeshData.INFLUENCES_PER_VERTEX;
        for (int influence = 0; influence < MeshData.INFLUENCES_PER_VERTEX; influence++) {
            bytes.putShort(data.jointIndices()[skinBase + influence]);
        }
        for (int influence = 0; influence < MeshData.INFLUENCES_PER_VERTEX; influence++) {
            bytes.putFloat(data.jointWeights()[skinBase + influence]);
        }
    }

    static IndexFormat indexFormatFor(MeshData data) {
        return data.vertexCount() <= MAX_SHORT_INDEXED_VERTICES ? IndexFormat.UINT16 : IndexFormat.UINT32;
    }

    static ByteBuffer packIndices(int[] indices, IndexFormat format) {
        return switch (format) {
            case UINT16 -> packShortIndices(indices);
            case UINT32 -> packIntIndices(indices);
        };
    }

    private static ByteBuffer packShortIndices(int[] indices) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(indices.length * Short.BYTES);
        for (int index : indices) {
            bytes.putShort((short) index);
        }
        bytes.flip();
        return bytes;
    }

    private static ByteBuffer packIntIndices(int[] indices) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(indices.length * Integer.BYTES);
        bytes.asIntBuffer().put(indices);
        return bytes;
    }
}
