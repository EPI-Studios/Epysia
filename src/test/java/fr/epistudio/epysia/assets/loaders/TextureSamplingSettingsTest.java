package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.render.texture.Texture2D;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TextureSamplingSettingsTest {

    @Test
    void linearFilteringEnablesMipmapsByDefault() {
        TextureImportSettings settings = TextureImportSettings.from(
                Map.of(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_LINEAR),
                AssetVariant.none());

        assertTrue(settings.mipmaps());
        assertEquals(TextureImportSettings.DEFAULT_ANISOTROPY, settings.anisotropy());
    }

    @Test
    void pointFilteringDisablesMipmapsAndAnisotropyByDefault() {
        TextureImportSettings settings = TextureImportSettings.from(
                Map.of(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_POINT),
                AssetVariant.none());

        assertFalse(settings.mipmaps());
        assertEquals(1, settings.anisotropy());
        assertFalse(Texture2D.samplingOf(settings).mipmaps());
    }

    @Test
    void anExplicitMipmapSettingOverridesTheFilterDefault() {
        TextureImportSettings settings = TextureImportSettings.from(
                Map.of(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_POINT,
                        TextureImportSettings.MIPMAPS_KEY, "true"),
                AssetVariant.none());

        assertTrue(settings.mipmaps());
        assertTrue(Texture2D.samplingOf(settings).mipmaps());
    }

    @Test
    void anisotropyIsClampedAndFallsBackWhenMalformed() {
        assertEquals(TextureImportSettings.MAXIMUM_ANISOTROPY, anisotropyFor("64"));
        assertEquals(1, anisotropyFor("0"));
        assertEquals(TextureImportSettings.DEFAULT_ANISOTROPY, anisotropyFor("not a number"));
    }

    private static int anisotropyFor(String declared) {
        return TextureImportSettings.from(
                Map.of(TextureImportSettings.FILTER_KEY, TextureImportSettings.FILTER_LINEAR,
                        TextureImportSettings.ANISOTROPY_KEY, declared),
                AssetVariant.none()).anisotropy();
    }

    @Test
    void mipLevelCountCoversTheWholeChain() {
        assertEquals(1, Texture2D.mipLevelsFor(1, 1));
        assertEquals(9, Texture2D.mipLevelsFor(256, 256));
        assertEquals(11, Texture2D.mipLevelsFor(1024, 16));
    }
}
