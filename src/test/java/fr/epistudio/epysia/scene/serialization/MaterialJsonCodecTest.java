package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialJsonCodecTest {

    @Test
    void singleMaterialRoundTripsThroughJson() {
        LitMaterial original = new LitMaterial();
        original.setSurfaceShaderPath("shaders/dissolve.surf.glsl");
        original.setFloat("dissolveProgress", 0.7f);
        original.roughness = 0.2f;
        MaterialJsonCodec codec = new MaterialJsonCodec();
        String json = codec.writeSingle(original);
        Optional<Material> decoded = codec.readSingle(json);
        assertTrue(decoded.isPresent());
        LitMaterial lit = (LitMaterial) decoded.get();
        assertEquals("shaders/dissolve.surf.glsl", lit.surfaceShaderPath());
        assertEquals(0.2f, lit.roughness);
        assertTrue(lit.surfaceUniforms().value("dissolveProgress").isPresent());
    }
}
