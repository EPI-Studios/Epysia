package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureWrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TexturePathPrefixesTest {

    @Test
    void combinedSrgbAndClampPrefixesParseInAnyOrder() {
        TexturePathPrefixes.ParsedPath parsed = TexturePathPrefixes.parse("srgb:clamp:foo.png");
        assertEquals(TextureFormat.SRGB8_ALPHA8, parsed.format());
        assertEquals(TextureWrap.CLAMP_TO_EDGE, parsed.wrap());
        assertEquals("foo.png", parsed.remainder());
    }

    @Test
    void clampThenSrgbPrefixParsesTheSameAsSrgbThenClamp() {
        TexturePathPrefixes.ParsedPath parsed = TexturePathPrefixes.parse("clamp:srgb:foo.png");
        assertEquals(TextureFormat.SRGB8_ALPHA8, parsed.format());
        assertEquals(TextureWrap.CLAMP_TO_EDGE, parsed.wrap());
        assertEquals("foo.png", parsed.remainder());
    }

    @Test
    void mirrorPrefixParsesToMirroredRepeatWrap() {
        TexturePathPrefixes.ParsedPath parsed = TexturePathPrefixes.parse("mirror:foo.png");
        assertEquals(TextureFormat.RGBA8, parsed.format());
        assertEquals(TextureWrap.MIRRORED_REPEAT, parsed.wrap());
        assertEquals("foo.png", parsed.remainder());
    }

    @Test
    void unprefixedPathDefaultsToRgbaAndRepeat() {
        TexturePathPrefixes.ParsedPath parsed = TexturePathPrefixes.parse("foo.png");
        assertEquals(TextureFormat.RGBA8, parsed.format());
        assertEquals(TextureWrap.REPEAT, parsed.wrap());
        assertEquals("foo.png", parsed.remainder());
    }

    @Test
    void stripPrefixesReturnsOnlyTheRemainder() {
        assertEquals("foo.png", TexturePathPrefixes.stripPrefixes("srgb:clamp:foo.png"));
    }
}
