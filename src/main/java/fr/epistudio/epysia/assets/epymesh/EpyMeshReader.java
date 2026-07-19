package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EpyMeshReader {

    private EpyMeshReader() {
    }

    public static EpyMesh read(byte[] data) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
            int flags = readHeader(stream);
            MeshData mesh = readMesh(stream);
            Optional<BakedCollider> collider = readCollider(stream, flags);
            return new EpyMesh(mesh, collider);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to decode .epymesh: " + exception.getMessage(), exception);
        }
    }

    public static EpyMesh readFile(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read .epymesh from " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static int readHeader(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != EpyMeshFormat.MAGIC) {
            throw new EpysiaException("Bad .epymesh magic: expected " + EpyMeshFormat.MAGIC + " but got " + magic + ".");
        }
        int version = stream.readInt();
        if (version != EpyMeshFormat.VERSION) {
            throw new EpysiaException("Unsupported .epymesh version: expected " + EpyMeshFormat.VERSION + " but got " + version + ".");
        }
        return stream.readInt();
    }

    private static MeshData readMesh(DataInputStream stream) throws IOException {
        float[] positions = readFloats(stream);
        float[] normals = readFloats(stream);
        float[] uvs = readFloats(stream);
        float[] tangents = readFloats(stream);
        int[] indices = readInts(stream);
        List<Submesh> submeshes = readSubmeshes(stream);
        return new MeshData(positions, normals, uvs, tangents, indices, submeshes);
    }

    private static Optional<BakedCollider> readCollider(DataInputStream stream, int flags) throws IOException {
        if ((flags & EpyMeshFormat.HAS_BAKED_COLLIDER) == 0) {
            return Optional.empty();
        }
        float[] triangleVertices = readFloats(stream);
        int[] triangleIndices = readInts(stream);
        float[] convexVertices = readFloats(stream);
        return Optional.of(new BakedCollider(triangleVertices, triangleIndices, convexVertices));
    }

    private static List<Submesh> readSubmeshes(DataInputStream stream) throws IOException {
        int count = stream.readInt();
        List<Submesh> submeshes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            submeshes.add(new Submesh(stream.readInt(), stream.readInt(), stream.readInt()));
        }
        return submeshes;
    }

    private static float[] readFloats(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        float[] values = new float[length];
        for (int index = 0; index < length; index++) {
            values[index] = stream.readFloat();
        }
        return values;
    }

    private static int[] readInts(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            values[index] = stream.readInt();
        }
        return values;
    }
}
