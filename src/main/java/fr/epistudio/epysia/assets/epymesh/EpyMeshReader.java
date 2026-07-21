package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
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

    private record Header(int version, int flags) {
    }

    private record MeshBody(float[] positions, float[] normals, float[] uvs, float[] tangents,
                             int[] indices, List<Submesh> submeshes) {
    }

    private record SkinArrays(short[] jointIndices, float[] jointWeights) {
    }

    private EpyMeshReader() {
    }

    public static EpyMesh read(byte[] data) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
            Header header = readHeader(stream);
            boolean skinned = (header.flags() & EpyMeshFormat.HAS_SKIN) != 0;
            MeshBody body = readMeshBody(stream);
            Optional<BakedCollider> collider = readCollider(stream, header.flags());
            SkinArrays skinArrays = skinned ? readSkinArrays(stream) : emptySkinArrays();
            Optional<Skeleton> skeleton = skinned ? Optional.of(readJoints(stream)) : Optional.empty();
            MeshData mesh = buildMesh(body, skinArrays);
            return new EpyMesh(mesh, collider, skeleton);
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

    private static Header readHeader(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != EpyMeshFormat.MAGIC) {
            throw new EpysiaException("Bad .epymesh magic: expected " + EpyMeshFormat.MAGIC + " but got " + magic + ".");
        }
        int version = stream.readInt();
        if (version != 1 && version != EpyMeshFormat.VERSION) {
            throw new EpysiaException("Unsupported .epymesh version: expected 1 or " + EpyMeshFormat.VERSION + " but got " + version + ".");
        }
        int flags = stream.readInt();
        return new Header(version, flags);
    }

    private static MeshBody readMeshBody(DataInputStream stream) throws IOException {
        float[] positions = readFloats(stream);
        float[] normals = readFloats(stream);
        float[] uvs = readFloats(stream);
        float[] tangents = readFloats(stream);
        int[] indices = readInts(stream);
        List<Submesh> submeshes = readSubmeshes(stream);
        return new MeshBody(positions, normals, uvs, tangents, indices, submeshes);
    }

    private static MeshData buildMesh(MeshBody body, SkinArrays skinArrays) {
        return new MeshData(body.positions(), body.normals(), body.uvs(), body.tangents(),
                skinArrays.jointIndices(), skinArrays.jointWeights(), body.indices(), body.submeshes());
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

    private static SkinArrays emptySkinArrays() {
        return new SkinArrays(new short[0], new float[0]);
    }

    private static SkinArrays readSkinArrays(DataInputStream stream) throws IOException {
        int jointIndexCount = stream.readInt();
        short[] jointIndices = new short[jointIndexCount];
        for (int index = 0; index < jointIndexCount; index++) {
            jointIndices[index] = stream.readShort();
        }
        float[] jointWeights = readFloats(stream);
        return new SkinArrays(jointIndices, jointWeights);
    }

    private static Skeleton readJoints(DataInputStream stream) throws IOException {
        int jointCount = stream.readInt();
        List<Joint> joints = new ArrayList<>(jointCount);
        for (int index = 0; index < jointCount; index++) {
            String name = stream.readUTF();
            int parentIndex = stream.readInt();
            float[] localBindTransform = readRawFloats(stream, 16);
            float[] inverseBindMatrix = readRawFloats(stream, 16);
            joints.add(new Joint(name, parentIndex, localBindTransform, inverseBindMatrix));
        }
        return new Skeleton(joints);
    }

    private static float[] readRawFloats(DataInputStream stream, int count) throws IOException {
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = stream.readFloat();
        }
        return values;
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
