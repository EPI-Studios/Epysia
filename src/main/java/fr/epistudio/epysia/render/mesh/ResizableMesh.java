package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.RenderBackend;

import java.nio.ByteBuffer;

public final class ResizableMesh {

    private final IndexFormat indexFormat;
    private final int vertexCapacityBytes;
    private final int indexCapacityBytes;
    private UploadedMesh mesh;

    ResizableMesh(UploadedMesh mesh, IndexFormat indexFormat, int vertexCapacityBytes, int indexCapacityBytes) {
        this.mesh = mesh;
        this.indexFormat = indexFormat;
        this.vertexCapacityBytes = vertexCapacityBytes;
        this.indexCapacityBytes = indexCapacityBytes;
    }

    public UploadedMesh mesh() {
        return mesh;
    }

    public int vertexCapacityBytes() {
        return vertexCapacityBytes;
    }

    public int indexCapacityBytes() {
        return indexCapacityBytes;
    }

    public boolean update(RenderBackend backend, MeshData data) {
        if (data.hasLightmapUvs()) {
            throw new EpysiaException("A resizable mesh cannot carry lightmap UVs.");
        }
        if (mesh.submeshes().size() != data.submeshes().size()) {
            return false;
        }
        ByteBuffer vertices = MeshUploader.interleaveVertices(data);
        ByteBuffer indices = MeshUploader.packIndices(data.indices(), indexFormat);
        if (vertices.remaining() > vertexCapacityBytes || indices.remaining() > indexCapacityBytes) {
            return false;
        }
        writeGeometry(backend, vertices, indices, data);
        return true;
    }

    private void writeGeometry(RenderBackend backend, ByteBuffer vertices, ByteBuffer indices, MeshData data) {
        backend.writeBuffer(mesh.vertexBuffer(), vertices, 0L);
        backend.writeBuffer(mesh.indexBuffer(), indices, 0L);
        for (int slot = 0; slot < data.submeshes().size(); slot++) {
            Submesh submesh = data.submeshes().get(slot);
            backend.updateMeshRange(mesh.submeshes().get(slot).handle(),
                    submesh.indexOffset(), submesh.indexCount());
        }
        mesh = new UploadedMesh(mesh.vertexBuffer(), mesh.indexBuffer(), mesh.submeshes(),
                Aabb.fromPositions(data.positions()), mesh.skinned(), mesh.vertexColored(),
                mesh.skeleton(), mesh.lightmapUvs());
    }

    public void destroy(RenderBackend backend) {
        mesh.destroy(backend);
    }
}
