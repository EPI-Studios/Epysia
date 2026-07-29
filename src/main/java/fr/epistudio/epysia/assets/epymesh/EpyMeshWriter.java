package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
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
        return write(mesh, collider, Optional.empty());
    }

    public static byte[] write(MeshData mesh, Optional<BakedCollider> collider, Optional<Skeleton> skeleton) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            requireSkeletonWhenSkinned(mesh, skeleton);
            writeHeader(stream, collider, mesh);
            writeMesh(stream, mesh);
            if (collider.isPresent()) {
                writeCollider(stream, collider.get());
            }
            if (mesh.hasSkin()) {
                writeSkin(stream, mesh, skeleton.orElseThrow());
            }
            if (mesh.hasVertexColors()) {
                writeFloats(stream, mesh.vertexColors());
            }
            if (mesh.hasLightmapUvs()) {
                writeFloats(stream, mesh.lightmapUvs());
            }
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode .epymesh: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(Path path, MeshData mesh, Optional<BakedCollider> collider) {
        writeToFile(path, mesh, collider, Optional.empty());
    }

    public static void writeToFile(Path path, MeshData mesh, Optional<BakedCollider> collider, Optional<Skeleton> skeleton) {
        try {
            Files.write(path, write(mesh, collider, skeleton));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epymesh to " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void requireSkeletonWhenSkinned(MeshData mesh, Optional<Skeleton> skeleton) {
        if (mesh.hasSkin() && skeleton.isEmpty()) {
            throw new EpysiaException("Skinned mesh requires a skeleton to write .epymesh.");
        }
    }

    private static void writeHeader(DataOutputStream stream, Optional<BakedCollider> collider, MeshData mesh) throws IOException {
        stream.writeInt(EpyMeshFormat.MAGIC);
        stream.writeInt(EpyMeshFormat.VERSION);
        stream.writeInt(headerFlags(collider, mesh));
    }

    private static int headerFlags(Optional<BakedCollider> collider, MeshData mesh) {
        int flags = 0;
        if (collider.isPresent()) {
            flags |= EpyMeshFormat.HAS_BAKED_COLLIDER;
        }
        if (mesh.hasSkin()) {
            flags |= EpyMeshFormat.HAS_SKIN;
        }
        if (mesh.hasVertexColors()) {
            flags |= EpyMeshFormat.HAS_VERTEX_COLORS;
        }
        if (mesh.hasLightmapUvs()) {
            flags |= EpyMeshFormat.HAS_LIGHTMAP_UVS;
        }
        return flags;
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

    private static void writeSkin(DataOutputStream stream, MeshData mesh, Skeleton skeleton) throws IOException {
        stream.writeInt(mesh.jointIndices().length);
        for (short index : mesh.jointIndices()) {
            stream.writeShort(index);
        }
        writeFloats(stream, mesh.jointWeights());
        stream.writeInt(skeleton.jointCount());
        for (Joint joint : skeleton.joints()) {
            stream.writeUTF(joint.name());
            stream.writeInt(joint.parentIndex());
            writeRawFloats(stream, joint.localBindTransform());
            writeRawFloats(stream, joint.inverseBindMatrix());
        }
    }

    private static void writeRawFloats(DataOutputStream stream, float[] values) throws IOException {
        for (float value : values) {
            stream.writeFloat(value);
        }
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
