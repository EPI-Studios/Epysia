package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialReferenceTest {

    @Test
    void assetBackedMaterialSerializesAsReferenceInScenes() {
        LitMaterial material = new LitMaterial();
        material.setAssetPath("materials/shared.epymaterial");
        material.roughness = 0.1f;
        MaterialJsonCodec codec = new MaterialJsonCodec();
        JsonWriter writer = new JsonWriter();
        codec.writeMaterialArray(writer, List.of(material));
        String json = writer.toString();
        assertTrue(json.contains("\"asset\""));
        assertTrue(json.contains("materials/shared.epymaterial"));
        assertFalse(json.contains("uniforms"));
    }

    @Test
    void assetBackedMaterialStillWritesFullBodyAsSingleDocument() {
        LitMaterial material = new LitMaterial();
        material.setAssetPath("materials/shared.epymaterial");
        MaterialJsonCodec codec = new MaterialJsonCodec();
        String json = codec.writeSingle(material);
        assertFalse(json.contains("\"asset\""));
        assertTrue(json.contains("uniforms"));
    }

    @Test
    void referenceReadsBackAsPlaceholderCarryingThePath() {
        MaterialJsonCodec codec = new MaterialJsonCodec();
        Optional<Material> decoded = codec.readMaterial(
                Map.of("asset", "materials/shared.epymaterial"));
        assertTrue(decoded.isPresent());
        assertEquals("materials/shared.epymaterial", decoded.get().assetPath());
    }
}
