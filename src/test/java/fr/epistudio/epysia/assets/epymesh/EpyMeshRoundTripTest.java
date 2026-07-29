package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpyMeshRoundTripTest {

    private static MeshData sampleMesh() {
        float[] positions = {
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        float[] normals = {
                0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f
        };
        float[] uvs = {
                0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f
        };
        float[] tangents = {
                1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f
        };
        int[] indices = {0, 1, 2, 0, 2, 3};
        List<Submesh> submeshes = List.of(new Submesh(0, 3, 0), new Submesh(3, 3, 1));
        return new MeshData(positions, normals, uvs, tangents, new short[0], new float[0], indices, submeshes);
    }

    @Test
    void roundTripsMeshWithoutBakedCollider() {
        MeshData original = sampleMesh();

        EpyMesh decoded = EpyMeshReader.read(EpyMeshWriter.write(original, Optional.empty()));

        assertMeshEquals(original, decoded.mesh());
        assertTrue(decoded.collider().isEmpty());
    }

    @Test
    void roundTripsMeshWithBakedCollider() {
        MeshData original = sampleMesh();
        float[] triangleVertices = {0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f};
        int[] triangleIndices = {0, 1, 2};
        float[] convexVertices = {-1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 0.5f, -0.5f, 0.25f};
        BakedCollider baked = new BakedCollider(triangleVertices, triangleIndices, convexVertices);

        EpyMesh decoded = EpyMeshReader.read(EpyMeshWriter.write(original, Optional.of(baked)));

        assertMeshEquals(original, decoded.mesh());
        BakedCollider readBack = decoded.collider().orElseThrow();
        assertArrayEquals(triangleVertices, readBack.triangleVertices());
        assertArrayEquals(triangleIndices, readBack.triangleIndices());
        assertArrayEquals(convexVertices, readBack.convexVertices());
    }

    @Test
    void roundTripsMeshWithVertexColors() {
        MeshData original = coloredMesh();

        EpyMesh decoded = EpyMeshReader.read(EpyMeshWriter.write(original, Optional.empty()));

        assertTrue(decoded.mesh().hasVertexColors());
        assertArrayEquals(original.vertexColors(), decoded.mesh().vertexColors(), 0.0f);
        assertMeshEquals(original, decoded.mesh());
    }

    private static MeshData coloredMesh() {
        MeshData base = sampleMesh();
        float[] vertexColors = {
                1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 0.5f, 1.0f, 1.0f, 1.0f, 1.0f
        };
        return new MeshData(base.positions(), base.normals(), base.uvs(), base.tangents(), vertexColors,
                new short[0], new float[0], base.indices(), base.submeshes());
    }

    @Test
    void keepsLightmapUvsAcrossRoundTrip() {
        MeshData source = lightmappedMesh();

        EpyMesh restored = EpyMeshReader.read(EpyMeshWriter.write(source, Optional.empty(), Optional.empty()));

        assertTrue(restored.mesh().hasLightmapUvs(), "lightmap uvs must survive the round trip");
        assertArrayEquals(source.lightmapUvs(), restored.mesh().lightmapUvs(), 1.0e-6f);
        assertArrayEquals(source.uvs(), restored.mesh().uvs(), 1.0e-6f);
    }

    private static MeshData lightmappedMesh() {
        MeshData base = sampleMesh();
        float[] lightmapUvs = new float[base.vertexCount() * 2];
        for (int index = 0; index < lightmapUvs.length; index++) {
            lightmapUvs[index] = 0.125f * (index + 1);
        }
        return new MeshData(base.positions(), base.normals(), base.uvs(), lightmapUvs, base.tangents(),
                new float[0], new short[0], new float[0], base.indices(), base.submeshes());
    }

    @Test
    void rejectsWrongMagic() {
        byte[] corrupt = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

        assertThrows(EpysiaException.class, () -> EpyMeshReader.read(corrupt));
    }

    @Test
    void skinnedMeshRoundTripsWithSkeleton() {
        MeshData mesh = skinnedTriangle();
        Skeleton skeleton = new Skeleton(List.of(
                new Joint("root", -1, identityMatrix(), identityMatrix()),
                new Joint("tip", 0, identityMatrix(), identityMatrix())));

        byte[] encoded = EpyMeshWriter.write(mesh, Optional.empty(), Optional.of(skeleton));
        EpyMesh decoded = EpyMeshReader.read(encoded);

        assertTrue(decoded.mesh().hasSkin());
        assertArrayEquals(mesh.jointIndices(), decoded.mesh().jointIndices());
        assertArrayEquals(mesh.jointWeights(), decoded.mesh().jointWeights(), 0.0f);
        assertEquals(2, decoded.skeleton().orElseThrow().jointCount());
        assertEquals(skeleton.nameChecksum(), decoded.skeleton().orElseThrow().nameChecksum());
    }

    @Test
    void skinnedMeshWithColliderRoundTrips() {
        MeshData mesh = skinnedTriangle();
        float[] triangleVertices = {0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f};
        int[] triangleIndices = {0, 1, 2};
        float[] convexVertices = {-1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 0.5f, -0.5f, 0.25f};
        BakedCollider collider = new BakedCollider(triangleVertices, triangleIndices, convexVertices);
        Skeleton skeleton = new Skeleton(List.of(
                new Joint("root", -1, identityMatrix(), identityMatrix()),
                new Joint("tip", 0, identityMatrix(), identityMatrix())));

        byte[] encoded = EpyMeshWriter.write(mesh, Optional.of(collider), Optional.of(skeleton));
        EpyMesh decoded = EpyMeshReader.read(encoded);

        assertTrue(decoded.mesh().hasSkin());
        assertTrue(decoded.collider().isPresent());
        assertTrue(decoded.skeleton().isPresent());
        assertArrayEquals(triangleVertices, decoded.collider().orElseThrow().triangleVertices(), 0.0f);
    }

    @Test
    void skeletonJointsRoundTripExactly() {
        MeshData mesh = skinnedTriangle();
        float[] rootLocalTransform = identityMatrix();
        float[] tipLocalTransform = identityMatrix();
        tipLocalTransform[13] = 2.5f;
        float[] rootInverseTransform = identityMatrix();
        float[] tipInverseTransform = identityMatrix();
        tipInverseTransform[12] = -1.0f;
        Skeleton skeleton = new Skeleton(List.of(
                new Joint("root", -1, rootLocalTransform, rootInverseTransform),
                new Joint("tip", 0, tipLocalTransform, tipInverseTransform)));

        byte[] encoded = EpyMeshWriter.write(mesh, Optional.empty(), Optional.of(skeleton));
        EpyMesh decoded = EpyMeshReader.read(encoded);

        Skeleton decodedSkeleton = decoded.skeleton().orElseThrow();
        Joint decodedTip = decodedSkeleton.joints().get(1);
        assertEquals("tip", decodedTip.name());
        assertEquals(0, decodedTip.parentIndex());
        float[] decodedTipLocalTransform = decodedTip.localBindTransform();
        float[] decodedTipInverseTransform = decodedTip.inverseBindMatrix();
        assertEquals(2.5f, decodedTipLocalTransform[13]);
        assertEquals(-1.0f, decodedTipInverseTransform[12]);
        assertArrayEquals(tipLocalTransform, decodedTipLocalTransform, 0.0f);
        assertArrayEquals(tipInverseTransform, decodedTipInverseTransform, 0.0f);
    }

    @Test
    void versionOneFilesStillLoad() {
        byte[] versionOne = encodeVersionOne(staticTriangle());

        EpyMesh decoded = EpyMeshReader.read(versionOne);

        assertFalse(decoded.mesh().hasSkin());
        assertTrue(decoded.skeleton().isEmpty());
    }

    @Test
    void rejectsJointIndexOutOfBounds() {
        MeshData mesh = skinnedTriangleWithOutOfBoundsIndex();
        Skeleton skeleton = new Skeleton(List.of(
                new Joint("root", -1, identityMatrix(), identityMatrix()),
                new Joint("tip", 0, identityMatrix(), identityMatrix())));

        byte[] encoded = EpyMeshWriter.write(mesh, Optional.empty(), Optional.of(skeleton));

        assertThrows(EpysiaException.class, () -> EpyMeshReader.read(encoded));
    }

    private static MeshData staticTriangle() {
        float[] positions = {0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f};
        float[] normals = {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f};
        float[] uvs = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f};
        float[] tangents = {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f};
        int[] indices = {0, 1, 2};
        return new MeshData(positions, normals, uvs, tangents, new short[0], new float[0], indices, List.of());
    }

    private static MeshData skinnedTriangle() {
        MeshData base = staticTriangle();
        short[] jointIndices = {0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0};
        float[] jointWeights = {1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        return new MeshData(base.positions(), base.normals(), base.uvs(), base.tangents(),
                jointIndices, jointWeights, base.indices(), base.submeshes());
    }

    private static MeshData skinnedTriangleWithOutOfBoundsIndex() {
        MeshData base = staticTriangle();
        short[] jointIndices = {7, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0};
        float[] jointWeights = {1.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        return new MeshData(base.positions(), base.normals(), base.uvs(), base.tangents(),
                jointIndices, jointWeights, base.indices(), base.submeshes());
    }

    private static float[] identityMatrix() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    private static byte[] encodeVersionOne(MeshData mesh) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            stream.writeInt(EpyMeshFormat.MAGIC);
            stream.writeInt(1);
            stream.writeInt(0);
            writeFloatsForVersionOne(stream, mesh.positions());
            writeFloatsForVersionOne(stream, mesh.normals());
            writeFloatsForVersionOne(stream, mesh.uvs());
            writeFloatsForVersionOne(stream, mesh.tangents());
            writeIntsForVersionOne(stream, mesh.indices());
            stream.writeInt(mesh.submeshes().size());
            for (Submesh submesh : mesh.submeshes()) {
                stream.writeInt(submesh.indexOffset());
                stream.writeInt(submesh.indexCount());
                stream.writeInt(submesh.materialSlot());
            }
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode version 1 fixture: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    private static void writeFloatsForVersionOne(DataOutputStream stream, float[] values) throws IOException {
        stream.writeInt(values.length);
        for (float value : values) {
            stream.writeFloat(value);
        }
    }

    private static void writeIntsForVersionOne(DataOutputStream stream, int[] values) throws IOException {
        stream.writeInt(values.length);
        for (int value : values) {
            stream.writeInt(value);
        }
    }

    private static void assertMeshEquals(MeshData expected, MeshData actual) {
        assertArrayEquals(expected.positions(), actual.positions());
        assertArrayEquals(expected.normals(), actual.normals());
        assertArrayEquals(expected.uvs(), actual.uvs());
        assertArrayEquals(expected.tangents(), actual.tangents());
        assertArrayEquals(expected.indices(), actual.indices());
        assertEquals(expected.submeshes().size(), actual.submeshes().size());
        for (int index = 0; index < expected.submeshes().size(); index++) {
            Submesh expectedSubmesh = expected.submeshes().get(index);
            Submesh actualSubmesh = actual.submeshes().get(index);
            assertEquals(expectedSubmesh.indexOffset(), actualSubmesh.indexOffset());
            assertEquals(expectedSubmesh.indexCount(), actualSubmesh.indexCount());
            assertEquals(expectedSubmesh.materialSlot(), actualSubmesh.materialSlot());
        }
    }
}
