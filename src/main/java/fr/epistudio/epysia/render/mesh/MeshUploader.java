package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class MeshUploader {

    private MeshUploader() {
    }

    public static UploadedMesh upload(RenderBackend backend, MeshData data) {
        BufferHandle vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, interleaveVertices(data)));
        BufferHandle indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, packIndices(data.indices())));
        List<UploadedSubmesh> uploadedSubmeshes = new ArrayList<>(data.submeshes().size());
        for (Submesh submesh : data.submeshes()) {
            MeshHandle handle = backend.createMesh(new MeshDescriptor(
                    vertexBuffer,
                    indexBuffer,
                    submesh.indexOffset(),
                    submesh.indexCount(),
                    IndexFormat.UINT32
            ));
            uploadedSubmeshes.add(new UploadedSubmesh(handle, submesh.materialSlot()));
        }
        return new UploadedMesh(vertexBuffer, indexBuffer, uploadedSubmeshes, Aabb.fromPositions(data.positions()));
    }

    private static ByteBuffer interleaveVertices(MeshData data) {
        int vertexCount = data.vertexCount();
        boolean hasUvs = data.uvs().length > 0;
        boolean hasTangents = data.tangents().length > 0;
        ByteBuffer bytes = BufferUtils.createByteBuffer(vertexCount * MeshData.VERTEX_FLOAT_COUNT * Float.BYTES);
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
        }
        bytes.flip();
        return bytes;
    }

    private static ByteBuffer packIndices(int[] indices) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(indices.length * Integer.BYTES);
        bytes.asIntBuffer().put(indices);
        return bytes;
    }
}
