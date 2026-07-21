package fr.epistudio.epysia.editor.importer;

import fr.epistudio.epysia.assets.epymesh.EpyMesh;
import fr.epistudio.epysia.assets.epymesh.EpyMeshReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfImporterTest {

    private static final int FLOATS_PER_POSITION = 3;
    private static final int VERTEX_COUNT = 3;
    private static final int FIXTURE_BUFFER_BYTES = 280;

    @Test
    void importsSkinnedTriangle(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = GltfImporter.importFile(source, directory);
        assertEquals(1, result.meshFiles().size());
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        assertEquals(VERTEX_COUNT, decoded.mesh().vertexCount());
        assertTrue(decoded.mesh().hasSkin());
        assertEquals(2, decoded.skeleton().orElseThrow().jointCount());
        float weightSum = decoded.mesh().jointWeights()[4] + decoded.mesh().jointWeights()[5]
                + decoded.mesh().jointWeights()[6] + decoded.mesh().jointWeights()[7];
        assertEquals(1.0f, weightSum, 0.0001f);
    }

    @Test
    void mixedSkinnedAndRigidPrimitivesImportAsStatic(@TempDir Path directory) throws Exception {
        Path source = writeMixedFixture(directory);
        GltfImportResult result = GltfImporter.importFile(source, directory);
        assertEquals(1, result.meshFiles().size());
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        assertFalse(decoded.mesh().hasSkin());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("mixed")));
    }

    private static Path writeMixedFixture(Path directory) throws Exception {
        Files.write(directory.resolve("mixed.bin"), fixtureBuffer());
        Path gltf = directory.resolve("mixed.gltf");
        Files.writeString(gltf, mixedFixtureJson());
        return gltf;
    }

    private static String mixedFixtureJson() {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0, 1]}],
                  "nodes": [
                    {"name": "root", "children": [2]},
                    {"name": "model", "mesh": 0, "skin": 0},
                    {"name": "tip", "translation": [0, 1, 0]}
                  ],
                  "skins": [{"joints": [0, 2], "inverseBindMatrices": 5}],
                  "meshes": [{"primitives": [
                    {"attributes": {"POSITION": 0, "NORMAL": 1, "JOINTS_0": 2, "WEIGHTS_0": 3}, "indices": 4},
                    {"attributes": {"POSITION": 0, "NORMAL": 1}, "indices": 4}
                  ]}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5123, "count": 3, "type": "VEC4"},
                    {"bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC4"},
                    {"bufferView": 4, "componentType": 5123, "count": 3, "type": "SCALAR"},
                    {"bufferView": 5, "componentType": 5126, "count": 2, "type": "MAT4"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 48},
                    {"buffer": 0, "byteOffset": 144, "byteLength": 6},
                    {"buffer": 0, "byteOffset": 152, "byteLength": 128}
                  ],
                  "buffers": [{"uri": "mixed.bin", "byteLength": 280}]
                }
                """;
    }

    private static Path writeFixture(Path directory) throws Exception {
        Files.write(directory.resolve("triangle.bin"), fixtureBuffer());
        Path gltf = directory.resolve("triangle.gltf");
        Files.writeString(gltf, fixtureJson());
        return gltf;
    }

    private static byte[] fixtureBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(FIXTURE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            buffer.putFloat(0).putFloat(0).putFloat(1);
        }
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            buffer.putShort((short) 0).putShort((short) 1).putShort((short) 0).putShort((short) 0);
        }
        buffer.putFloat(1).putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(0.3f).putFloat(0.9f).putFloat(0).putFloat(0);
        buffer.putFloat(0.5f).putFloat(0.5f).putFloat(0).putFloat(0);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        buffer.putShort((short) 0);
        for (int joint = 0; joint < 2; joint++) {
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 4; column++) {
                    buffer.putFloat(row == column ? 1.0f : 0.0f);
                }
            }
        }
        return buffer.array();
    }

    private static String fixtureJson() {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0, 1]}],
                  "nodes": [
                    {"name": "root", "children": [2]},
                    {"name": "model", "mesh": 0, "skin": 0},
                    {"name": "tip", "translation": [0, 1, 0]}
                  ],
                  "skins": [{"joints": [0, 2], "inverseBindMatrices": 5}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1, "JOINTS_0": 2, "WEIGHTS_0": 3},
                    "indices": 4
                  }]}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5123, "count": 3, "type": "VEC4"},
                    {"bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC4"},
                    {"bufferView": 4, "componentType": 5123, "count": 3, "type": "SCALAR"},
                    {"bufferView": 5, "componentType": 5126, "count": 2, "type": "MAT4"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 48},
                    {"buffer": 0, "byteOffset": 144, "byteLength": 6},
                    {"buffer": 0, "byteOffset": 152, "byteLength": 128}
                  ],
                  "buffers": [{"uri": "triangle.bin", "byteLength": 280}]
                }
                """;
    }
}
