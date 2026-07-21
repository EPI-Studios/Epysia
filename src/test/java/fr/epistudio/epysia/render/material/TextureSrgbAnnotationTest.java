package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureSrgbAnnotationTest {

    @Test
    void colorTexturesAreMarkedSrgb() {
        assertTrue(isSrgb("albedo"));
        assertTrue(isSrgb("emissiveMap"));
    }

    @Test
    void dataTexturesStayLinear() {
        assertFalse(isSrgb("normalMap"));
        assertFalse(isSrgb("metallicRoughnessMap"));
        assertFalse(isSrgb("occlusionMap"));
    }

    @Test
    void srgbPrefixPrependsResolvePath() {
        assertEquals("srgb:albedo.png", TextureAssetLoader.SRGB_PREFIX + "albedo.png");
    }

    private static boolean isSrgb(String fieldName) {
        for (Field field : MaterialFields.textureFields(LitMaterial.class)) {
            if (field.getName().equals(fieldName)) {
                return field.getAnnotation(Texture.class).srgb();
            }
        }
        throw new IllegalStateException("No texture field named " + fieldName);
    }
}
