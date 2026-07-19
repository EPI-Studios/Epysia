package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class EpyMeshWriter {

    private EpyMeshWriter() {
    }

    public static byte[] write(MeshData mesh, Optional<BakedCollider> collider) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            writeHeader(stream, collider);
            writeMesh(stream, mesh);
            if (collider.isPresent()) {
                writeCollider(stream, collider.get());
            }
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode .epymesh: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(Path path, MeshData mesh, Optional<BakedCollider> collider) {
        try {
            Files.write(path, write(mesh, collider));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epymesh to " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void writeHeader(DataOutputStream stream, Optional<BakedCollider> collider) throws IOException {
        stream.writeInt(EpyMeshFormat.MAGIC);
        stream.writeInt(EpyMeshFormat.VERSION);
        stream.writeInt(collider.isPresent() ? EpyMeshFormat.HAS_BAKED_COLLIDER : 0);
    }

    private static void writeMesh(DataOutputStream stream, MeshData mesh) throws IOException {
        writeFloats(stream, mesh.positions());
        writeFloats(stream, mesh.normals());
        writeFloats(stream, mesh.uvs());
        writeFloats(stream, mesh.tangents());
        writeInts(stream, mesh.indices());
        writeSubmeshes(stream, mesh.submeshes());
    }

    private static void writeCollider(DataOutputStream stream, BakedCollider collider) throws IOException {
        writeFloats(stream, collider.triangleVertices());
        writeInts(stream, collider.triangleIndices());
        writeFloats(stream, collider.convexVertices());
    }

    private static void writeSubmeshes(DataOutputStream stream, List<Submesh> submeshes) throws IOException {
        stream.writeInt(submeshes.size());
        for (Submesh submesh : submeshes) {
            stream.writeInt(submesh.indexOffset());
            stream.writeInt(submesh.indexCount());
            stream.writeInt(submesh.materialSlot());
        }
    }

    private static void writeFloats(DataOutputStream stream, float[] values) throws IOException {
        stream.writeInt(values.length);
        for (float value : values) {
            stream.writeFloat(value);
        }
    }

    private static void writeInts(DataOutputStream stream, int[] values) throws IOException {
        stream.writeInt(values.length);
        for (int value : values) {
            stream.writeInt(value);
        }
    }
}
