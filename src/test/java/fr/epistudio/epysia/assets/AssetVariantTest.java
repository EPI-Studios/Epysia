package fr.epistudio.epysia.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AssetVariantTest {

    @Test
    void twoVariantsOfOneFileProduceDistinctFingerprints() {
        AssetVariant srgb = AssetVariant.of("colorSpace", "srgb");
        AssetVariant linear = AssetVariant.of("colorSpace", "linear");
        assertNotEquals(srgb.fingerprint(), linear.fingerprint());
    }

    @Test
    void theAbsenceOfOverridesHasAnEmptyFingerprint() {
        assertEquals("", AssetVariant.none().fingerprint());
    }

    @Test
    void declarationOrderDoesNotChangeTheFingerprint() {
        AssetVariant first = AssetVariant.of("wrap", "clamp").with("filter", "point");
        AssetVariant second = AssetVariant.of("filter", "point").with("wrap", "clamp");
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first, second);
    }
}
