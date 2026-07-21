package fr.epistudio.epysia.editor.importer;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.assets.epyclip.EpyClipReader;
import fr.epistudio.epysia.assets.epymesh.EpyMesh;
import fr.epistudio.epysia.assets.epymesh.EpyMeshReader;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.scene.serialization.MaterialJsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfImporterTest {

    private static final int FLOATS_PER_POSITION = 3;
    private static final int VERTEX_COUNT = 3;
    private static final int FIXTURE_BUFFER_BYTES = 320;

    @Test
    void importsSkinnedTriangle(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = runImport(source, directory);
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
    void importsAnimationAsClip(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = runImport(source, directory);
        assertEquals(1, result.clipFiles().size());
        Clip clip = EpyClipReader.readFile(result.clipFiles().get(0));
        assertEquals("wave", clip.name());
        assertEquals(1, clip.channels().size());
        assertEquals(ClipProperty.ROTATION, clip.channels().get(0).property());
        EpyMesh mesh = EpyMeshReader.readFile(result.meshFiles().get(0));
        assertEquals(mesh.skeleton().orElseThrow().nameChecksum(), clip.skeletonChecksum());
    }

    @Test
    void importsMaterialWithEmbeddedTexture(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = runImport(source, directory);
        assertEquals(1, result.materialFiles().size());
        String document = Files.readString(result.materialFiles().get(0));
        Material material = new MaterialJsonCodec().readSingle(document).orElseThrow();
        LitMaterial litMaterial = (LitMaterial) material;
        assertEquals(0.8f, litMaterial.roughness, 0.0001f);
        String albedoPath = litMaterial.texturePath("albedo").orElseThrow();
        assertTrue(Files.exists(directory.resolve(albedoPath)));
    }

    @Test
    void importsDoubleSidedMaterial(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = runImport(source, directory);
        String document = Files.readString(result.materialFiles().get(0));
        Material material = new MaterialJsonCodec().readSingle(document).orElseThrow();
        assertTrue(material.doubleSided());
    }

    @Test
    void mixedSkinnedAndRigidPrimitivesImportAsStatic(@TempDir Path directory) throws Exception {
        Path source = writeMixedFixture(directory);
        GltfImportResult result = runImport(source, directory);
        assertEquals(1, result.meshFiles().size());
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        assertFalse(decoded.mesh().hasSkin());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("mixed")));
    }

    @Test
    void duplicateMaterialNamesGetDistinctFiles(@TempDir Path directory) throws Exception {
        Path source = writeDuplicateMaterialNamesFixture(directory);
        GltfImportResult result = runImport(source, directory);
        assertEquals(3, result.materialFiles().size());
        Path first = result.materialFiles().get(0);
        Path second = result.materialFiles().get(1);
        assertTrue(Files.exists(first));
        assertTrue(Files.exists(second));
        assertFalse(first.equals(second));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Shared")));
    }

    private static Path writeDuplicateMaterialNamesFixture(Path directory) throws Exception {
        Files.write(directory.resolve("mixed.bin"), fixtureBuffer());
        Path gltf = directory.resolve("duplicate.gltf");
        Files.writeString(gltf, duplicateMaterialNamesFixtureJson());
        return gltf;
    }

    private static String duplicateMaterialNamesFixtureJson() {
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
                    {"attributes": {"POSITION": 0, "NORMAL": 1, "JOINTS_0": 2, "WEIGHTS_0": 3}, "indices": 4}
                  ]}],
                  "materials": [
                    {"name": "Shared", "pbrMetallicRoughness": {"baseColorFactor": [1.0, 1.0, 1.0, 1.0]}},
                    {"name": "Shared", "pbrMetallicRoughness": {"baseColorFactor": [0.5, 0.5, 0.5, 1.0]}, "alphaMode": "BLEND"},
                    {"name": "Glass", "pbrMetallicRoughness": {"baseColorFactor": [1.0, 1.0, 1.0, 1.0]},
                     "extensions": {"KHR_materials_transmission": {"transmissionFactor": 0.9}}}
                  ],
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

    @Test
    void blendAlphaModeMarksMaterialTransparent(@TempDir Path directory) throws Exception {
        Path source = writeDuplicateMaterialNamesFixture(directory);
        GltfImportResult result = runImport(source, directory);
        String document = Files.readString(result.materialFiles().get(1));
        Material material = new MaterialJsonCodec().readSingle(document).orElseThrow();
        assertTrue(((LitMaterial) material).transparent());
    }

    @Test
    void transmissionMarksMaterialTransparent(@TempDir Path directory) throws Exception {
        Path source = writeDuplicateMaterialNamesFixture(directory);
        GltfImportResult result = runImport(source, directory);
        String document = Files.readString(result.materialFiles().get(2));
        Material material = new MaterialJsonCodec().readSingle(document).orElseThrow();
        assertTrue(((LitMaterial) material).transparent());
    }

    @Test
    void importsNormalScaleOcclusionStrengthAndUntexturedEmissive(@TempDir Path directory) throws Exception {
        Path source = writeUvFixture(directory, "pbrfactors.gltf", pbrFactorsFixtureJson());
        GltfImportResult result = runImport(source, directory);
        String document = Files.readString(result.materialFiles().get(0));
        LitMaterial material = (LitMaterial) new MaterialJsonCodec().readSingle(document).orElseThrow();
        assertEquals(0.5f, material.normalScale, 0.0001f);
        assertEquals(0.25f, material.occlusionStrength, 0.0001f);
        assertEquals(3.0f, material.emissiveStrength, 0.0001f);
        assertTrue(material.texturePath("emissiveMap").isEmpty());
    }

    private String pbrFactorsFixtureJson() throws IOException {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "nodes": [{"name": "model", "mesh": 0}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2, "TEXCOORD_1": 3},
                    "indices": 4,
                    "material": 0
                  }]}],
                  "materials": [{
                    "name": "pbr",
                    "pbrMetallicRoughness": {"baseColorFactor": [1.0, 1.0, 1.0, 1.0]},
                    "normalTexture": {"index": 0, "scale": 0.5},
                    "occlusionTexture": {"index": 0, "strength": 0.25},
                    "emissiveFactor": [1.0, 0.0, 0.0],
                    "extensions": {"KHR_materials_emissive_strength": {"emissiveStrength": 3.0}}
                  }],
                  "textures": [{"source": 0}],
                  "images": [{"uri": "%s"}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC2"},
                    {"bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC2"},
                    {"bufferView": 4, "componentType": 5123, "count": 3, "type": "SCALAR"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 120, "byteLength": 6}
                  ],
                  "buffers": [{"uri": "uv.bin", "byteLength": 126}]
                }
                """.formatted(pngDataUri());
    }

    @Test
    void morphTargetsAreSkippedWithWarning(@TempDir Path directory) throws Exception {
        Path source = writeMorphFixture(directory);
        GltfImportResult result = runImport(source, directory);
        assertEquals(1, result.meshFiles().size());
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        assertEquals(VERTEX_COUNT, decoded.mesh().vertexCount());
        assertFalse(decoded.mesh().hasSkin());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("morph target")));
    }

    private static Path writeMorphFixture(Path directory) throws Exception {
        Files.write(directory.resolve("morph.bin"), morphFixtureBuffer());
        Path gltf = directory.resolve("morph.gltf");
        Files.writeString(gltf, morphFixtureJson());
        return gltf;
    }

    private static byte[] morphFixtureBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(78).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            buffer.putFloat(0).putFloat(0).putFloat(1);
        }
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        return buffer.array();
    }

    private static String morphFixtureJson() {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "nodes": [{"name": "model", "mesh": 0}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1},
                    "indices": 2,
                    "targets": [{"POSITION": 0}]
                  }]}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 6}
                  ],
                  "buffers": [{"uri": "morph.bin", "byteLength": 78}]
                }
                """;
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

    @Test
    void importAssemblesPrefabWithTransformMaterialAndAnimator(@TempDir Path directory) throws Exception {
        Path source = writeFixture(directory);
        GltfImportResult result = runImport(source, directory);
        Path prefab = result.prefabFile().orElseThrow();
        assertTrue(Files.exists(prefab));
        String document = Files.readString(prefab);
        assertTrue(document.contains(result.meshFiles().get(0).toString()));
        assertTrue(document.contains(result.materialFiles().get(0).toString()));
        assertTrue(document.contains(result.clipFiles().get(0).toString()));
        assertTrue(document.contains("Animator"));
        assertTrue(document.contains("2.0") && document.contains("3.0") && document.contains("4.0"));
    }

    @Test
    void bakesBaseColorTextureTransformIntoUvs(@TempDir Path directory) throws Exception {
        Path source = writeUvFixture(directory, "transform.gltf", textureTransformFixtureJson());
        GltfImportResult result = runImport(source, directory);
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        float[] uvs = decoded.mesh().uvs();
        assertEquals(2.5f, uvs[2], 0.0001f);
        assertEquals(1.0f, uvs[3], 0.0001f);
        assertEquals(0.5f, uvs[4], 0.0001f);
        assertEquals(-1.0f, uvs[5], 0.0001f);
    }

    @Test
    void bakesClockwiseRotationTextureTransformIntoUvs(@TempDir Path directory) throws Exception {
        Path source = writeUvFixture(directory, "rotation.gltf", rotationTransformFixtureJson());
        GltfImportResult result = runImport(source, directory);
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        float[] uvs = decoded.mesh().uvs();
        assertEquals(0.0f, uvs[2], 0.0001f);
        assertEquals(2.0f, uvs[3], 0.0001f);
    }

    @Test
    void selectsUvSetFromBaseColorTexCoord(@TempDir Path directory) throws Exception {
        Path source = writeUvFixture(directory, "texcoord.gltf", texCoordSelectionFixtureJson());
        GltfImportResult result = runImport(source, directory);
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        float[] uvs = decoded.mesh().uvs();
        assertEquals(0.25f, uvs[0], 0.0001f);
        assertEquals(0.25f, uvs[1], 0.0001f);
    }

    @Test
    void triangleStripExpandsToTriangleList(@TempDir Path directory) throws Exception {
        Path source = writeStripFixture(directory);
        GltfImportResult result = runImport(source, directory);
        EpyMesh decoded = EpyMeshReader.readFile(result.meshFiles().get(0));
        int[] indices = decoded.mesh().indices();
        assertEquals(6, indices.length);
        assertArrayEquals(new int[] {0, 1, 2, 1, 3, 2}, indices);
    }

    private static Path writeUvFixture(Path directory, String fileName, String json) throws Exception {
        Files.write(directory.resolve("uv.bin"), uvFixtureBuffer());
        Path gltf = directory.resolve(fileName);
        Files.writeString(gltf, json);
        return gltf;
    }

    private static byte[] uvFixtureBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(126).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            buffer.putFloat(0).putFloat(0).putFloat(1);
        }
        buffer.putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0);
        buffer.putFloat(0).putFloat(1);
        buffer.putFloat(0.25f).putFloat(0.75f);
        buffer.putFloat(0.25f).putFloat(0.75f);
        buffer.putFloat(0.25f).putFloat(0.75f);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        return buffer.array();
    }

    private static String textureTransformFixtureJson() throws IOException {
        return uvFixtureJson("""
                "baseColorTexture": {"index": 0, "extensions": {"KHR_texture_transform": {"offset": [0.5, 0], "scale": [2, 2]}}}""");
    }

    private static String rotationTransformFixtureJson() throws IOException {
        return uvFixtureJson("""
                "baseColorTexture": {"index": 0, "extensions": {"KHR_texture_transform": {"offset": [0, 0], "scale": [1, 1], "rotation": 1.5707963267948966}}}""");
    }

    private static String texCoordSelectionFixtureJson() throws IOException {
        return uvFixtureJson("\"baseColorTexture\": {\"index\": 0, \"texCoord\": 1}");
    }

    @Test
    void missingExternalTextureFallsBackToPlaceholder(@TempDir Path directory) throws Exception {
        Files.write(directory.resolve("uv.bin"), uvFixtureBuffer());
        Path gltf = directory.resolve("missing.gltf");
        Files.writeString(gltf, uvFixtureJson("\"baseColorTexture\": {\"index\": 0}", "does-not-exist.png"));
        GltfImportResult result = runImport(gltf, directory);
        assertEquals(1, result.materialFiles().size());
        assertEquals(1, result.warnings().stream().filter(warning -> warning.contains("Missing texture")).count());
        String document = Files.readString(result.materialFiles().get(0));
        Material material = new MaterialJsonCodec().readSingle(document).orElseThrow();
        String albedoPath = ((LitMaterial) material).texturePath("albedo").orElseThrow();
        assertTrue(Files.exists(directory.resolve(albedoPath)));
    }

    private static String uvFixtureJson(String baseColorTexture) throws IOException {
        return uvFixtureJson(baseColorTexture, pngDataUri());
    }

    private static String uvFixtureJson(String baseColorTexture, String imageUri) {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "nodes": [{"name": "model", "mesh": 0}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2, "TEXCOORD_1": 3},
                    "indices": 4,
                    "material": 0
                  }]}],
                  "materials": [{"name": "surface", "pbrMetallicRoughness": {%s}}],
                  "textures": [{"source": 0}],
                  "images": [{"uri": "%s"}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC2"},
                    {"bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC2"},
                    {"bufferView": 4, "componentType": 5123, "count": 3, "type": "SCALAR"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 120, "byteLength": 6}
                  ],
                  "buffers": [{"uri": "uv.bin", "byteLength": 126}]
                }
                """.formatted(baseColorTexture, imageUri);
    }

    private static Path writeStripFixture(Path directory) throws Exception {
        Files.write(directory.resolve("strip.bin"), stripFixtureBuffer());
        Path gltf = directory.resolve("strip.gltf");
        Files.writeString(gltf, stripFixtureJson());
        return gltf;
    }

    private static byte[] stripFixtureBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(104).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putFloat(1).putFloat(1).putFloat(0);
        for (int vertex = 0; vertex < 4; vertex++) {
            buffer.putFloat(0).putFloat(0).putFloat(1);
        }
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2).putShort((short) 3);
        return buffer.array();
    }

    private static String stripFixtureJson() {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "nodes": [{"name": "model", "mesh": 0}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1},
                    "indices": 2,
                    "mode": 5
                  }]}],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5123, "count": 4, "type": "SCALAR"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 48},
                    {"buffer": 0, "byteOffset": 48, "byteLength": 48},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 8}
                  ],
                  "buffers": [{"uri": "strip.bin", "byteLength": 104}]
                }
                """;
    }

    private static GltfImportResult runImport(Path source, Path directory) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return GltfImporter.importFile(source, directory, registry);
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
        buffer.putFloat(0.0f).putFloat(1.0f);
        buffer.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(1.0f);
        buffer.putFloat(0.0f).putFloat(0.0f).putFloat(0.7071f).putFloat(0.7071f);
        return buffer.array();
    }

    private static String fixtureJson() throws IOException {
        return """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0, 1]}],
                  "nodes": [
                    {"name": "root", "children": [2]},
                    {"name": "model", "mesh": 0, "skin": 0, "translation": [2, 3, 4]},
                    {"name": "tip", "translation": [0, 1, 0]}
                  ],
                  "skins": [{"joints": [0, 2], "inverseBindMatrices": 5}],
                  "meshes": [{"primitives": [{
                    "attributes": {"POSITION": 0, "NORMAL": 1, "JOINTS_0": 2, "WEIGHTS_0": 3},
                    "indices": 4,
                    "material": 0
                  }]}],
                  "materials": [{
                    "name": "skin",
                    "doubleSided": true,
                    "pbrMetallicRoughness": {
                      "baseColorFactor": [0.5, 0.25, 1.0, 1.0],
                      "metallicFactor": 0.0,
                      "roughnessFactor": 0.8,
                      "baseColorTexture": {"index": 0}
                    }
                  }],
                  "textures": [{"source": 0}],
                  "images": [{"uri": "%s"}],
                  "animations": [{
                    "name": "wave",
                    "channels": [{"sampler": 0, "target": {"node": 2, "path": "rotation"}}],
                    "samplers": [{"input": 6, "interpolation": "LINEAR", "output": 7}]
                  }],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3"},
                    {"bufferView": 2, "componentType": 5123, "count": 3, "type": "VEC4"},
                    {"bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC4"},
                    {"bufferView": 4, "componentType": 5123, "count": 3, "type": "SCALAR"},
                    {"bufferView": 5, "componentType": 5126, "count": 2, "type": "MAT4"},
                    {"bufferView": 6, "componentType": 5126, "count": 2, "type": "SCALAR"},
                    {"bufferView": 7, "componentType": 5126, "count": 2, "type": "VEC4"}
                  ],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 72, "byteLength": 24},
                    {"buffer": 0, "byteOffset": 96, "byteLength": 48},
                    {"buffer": 0, "byteOffset": 144, "byteLength": 6},
                    {"buffer": 0, "byteOffset": 152, "byteLength": 128},
                    {"buffer": 0, "byteOffset": 280, "byteLength": 8},
                    {"buffer": 0, "byteOffset": 288, "byteLength": 32}
                  ],
                  "buffers": [{"uri": "triangle.bin", "byteLength": 320}]
                }
                """.formatted(pngDataUri());
    }

    private static String pngDataUri() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
}
