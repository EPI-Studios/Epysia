package fr.epistudio.epysia.assets.epymesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rejectsWrongMagic() {
        byte[] corrupt = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

        assertThrows(EpysiaException.class, () -> EpyMeshReader.read(corrupt));
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
