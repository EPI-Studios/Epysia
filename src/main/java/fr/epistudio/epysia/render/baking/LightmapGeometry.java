package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.mesh.UploadedSubmesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Optional;

record LightmapGeometry(float[] positions, float[] normals, float[] lightmapUvs, int[] indices) {

    private static final int POSITION_OFFSET = 0;
    private static final int NORMAL_OFFSET = 12;

    static Optional<LightmapGeometry> read(RenderBackend backend, UploadedMesh mesh, Matrix4f worldMatrix) {
        if (mesh.lightmapUvs().isEmpty()) {
            return Optional.empty();
        }
        int vertexCount = (int) (mesh.lightmapUvs().orElseThrow().byteSize() / (2 * Float.BYTES));
        if (vertexCount <= 0) {
            return Optional.empty();
        }
        float[] lightmapUvs = readLightmapUvs(backend, mesh, vertexCount);
        ByteBuffer vertices = readVertices(backend, mesh, vertexCount);
        int stride = MeshShaderBindings.vertexStride(mesh.skinned(), mesh.vertexColored());
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        transformVertices(vertices, stride, vertexCount, worldMatrix, positions, normals);
        return Optional.of(new LightmapGeometry(positions, normals, lightmapUvs, readIndices(backend, mesh)));
    }

    private static float[] readLightmapUvs(RenderBackend backend, UploadedMesh mesh, int vertexCount) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(vertexCount * 2 * Float.BYTES);
        backend.readBuffer(mesh.lightmapUvs().orElseThrow().buffer(), bytes, 0L);
        float[] uvs = new float[vertexCount * 2];
        bytes.asFloatBuffer().get(uvs);
        return uvs;
    }

    private static ByteBuffer readVertices(RenderBackend backend, UploadedMesh mesh, int vertexCount) {
        int stride = MeshShaderBindings.vertexStride(mesh.skinned(), mesh.vertexColored());
        ByteBuffer bytes = BufferUtils.createByteBuffer(vertexCount * stride);
        backend.readBuffer(mesh.vertexBuffer(), bytes, 0L);
        return bytes;
    }

    private static void transformVertices(ByteBuffer vertices, int stride, int vertexCount, Matrix4f worldMatrix,
                                          float[] positions, float[] normals) {
        Matrix4f normalMatrix = new Matrix4f(worldMatrix).invert().transpose();
        Vector3f scratch = new Vector3f();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * stride;
            scratch.set(vertices.getFloat(base + POSITION_OFFSET), vertices.getFloat(base + POSITION_OFFSET + 4),
                    vertices.getFloat(base + POSITION_OFFSET + 8));
            worldMatrix.transformPosition(scratch);
            positions[vertex * 3] = scratch.x;
            positions[vertex * 3 + 1] = scratch.y;
            positions[vertex * 3 + 2] = scratch.z;
            scratch.set(vertices.getFloat(base + NORMAL_OFFSET), vertices.getFloat(base + NORMAL_OFFSET + 4),
                    vertices.getFloat(base + NORMAL_OFFSET + 8));
            normalMatrix.transformDirection(scratch).normalize();
            normals[vertex * 3] = scratch.x;
            normals[vertex * 3 + 1] = scratch.y;
            normals[vertex * 3 + 2] = scratch.z;
        }
    }

    private static int[] readIndices(RenderBackend backend, UploadedMesh mesh) {
        int total = 0;
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            total += backend.meshIndexCount(submesh.handle());
        }
        IndexFormat format = backend.meshIndexFormat(mesh.submeshes().get(0).handle());
        int elementBytes = format == IndexFormat.UINT16 ? Short.BYTES : Integer.BYTES;
        int[] indices = new int[total];
        int cursor = 0;
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            cursor = appendSubmeshIndices(backend, mesh, submesh, elementBytes, indices, cursor);
        }
        return indices;
    }

    private static int appendSubmeshIndices(RenderBackend backend, UploadedMesh mesh, UploadedSubmesh submesh,
                                            int elementBytes, int[] indices, int cursor) {
        int count = backend.meshIndexCount(submesh.handle());
        long byteOffset = (long) backend.meshFirstIndex(submesh.handle()) * elementBytes;
        ByteBuffer bytes = BufferUtils.createByteBuffer(count * elementBytes);
        backend.readBuffer(mesh.indexBuffer(), bytes, byteOffset);
        for (int index = 0; index < count; index++) {
            indices[cursor + index] = elementBytes == Short.BYTES
                    ? Short.toUnsignedInt(bytes.getShort(index * elementBytes))
                    : bytes.getInt(index * elementBytes);
        }
        return cursor + count;
    }
}
