package fr.epistudio.epysia.render.shader;

import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaderUniformLayoutTest {

    @Test
    void vector3AlignsToSixteenBytes() {
        ParsedSource parsed = ShaderUniformParser.parse("""
                uniform float first;
                uniform vec3 second;
                """);
        assertEquals(0, parsed.byteOffsetsByName().get("first"));
        assertEquals(16, parsed.byteOffsetsByName().get("second"));
    }

    @Test
    void floatAfterVector3PacksIntoTheTrailingComponent() {
        ParsedSource parsed = ShaderUniformParser.parse("""
                uniform vec3 first;
                uniform float second;
                """);
        assertEquals(0, parsed.byteOffsetsByName().get("first"));
        assertEquals(12, parsed.byteOffsetsByName().get("second"));
    }

    @Test
    void bufferSizeRoundsUpToSixteen() {
        ParsedSource parsed = ShaderUniformParser.parse("uniform float only;");
        assertEquals(16, parsed.uniformBufferSize());
    }

    @Test
    void floatArrayElementsStrideSixteen() {
        ParsedSource parsed = ShaderUniformParser.parse("""
                uniform float values[4];
                uniform float after;
                """);
        assertEquals(0, parsed.byteOffsetsByName().get("values"));
        assertEquals(64, parsed.byteOffsetsByName().get("after"));
    }

    @Test
    void samplersOccupyNoBufferSpace() {
        ParsedSource parsed = ShaderUniformParser.parse("""
                uniform sampler2D albedoDetail;
                uniform float scale;
                """);
        assertEquals(0, parsed.byteOffsetsByName().get("scale"));
        assertEquals(1, parsed.samplerDeclarations().size());
    }
}
