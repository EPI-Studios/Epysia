package fr.epistudio.epysia.render.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoutCompositionTest {
    private static LoadedShader resource(String path) {
        try (InputStream stream = CutoutCompositionTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing resource " + path);
            }
            return new LoadedShader(new String(stream.readAllBytes(), StandardCharsets.UTF_8), List.of());
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void impostorSurfaceDeclaresCutout() {
        assertTrue(SurfaceShaderComposer.declaresCutout(resource("shaders/impostor.surf.glsl")));
    }

    @Test
    void surfaceWithoutColorOrShadeDeclaresNoCutout() {
        assertFalse(SurfaceShaderComposer.declaresCutout(new LoadedShader("""
                void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal,
                                   in vec2 uv, in float time) {
                }
                """, List.of())));
    }

    @Test
    void composedPrepassFragmentIsWellFormed() {
        String composed = SurfaceShaderComposer.composeCutoutFragment(
                resource("shaders/depth_prepass_masked.frag.glsl"),
                resource("shaders/impostor.surf.glsl")).source();

        assertFalse(composed.contains("// SURFACE_CUTOUT_CALL"), "cutout marker not replaced");
        assertFalse(composed.contains("// SURFACE_FUNCTIONS"), "functions marker not replaced");
        assertTrue(composed.contains("void surfaceShade("), "surface shade body missing");
        assertTrue(composed.contains("mat4 objectToWorld()"), "object helpers missing");
        assertTrue(composed.contains("surfaceInstanceIndex"), "instance index unavailable");
        assertTrue(composed.contains("uniform sampler2D impostorAlbedoAtlas"), "surface samplers missing");
        assertFalse(composed.contains("void surfaceLight("), "lighting hook must stay out of the prepass");
        assertEquals(countOf(composed, "void main("), 1, "exactly one entry point expected");
        assertBalanced(composed);
    }

    private static void assertBalanced(String source) {
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
            }
            assertTrue(depth >= 0, "unbalanced closing brace at " + index);
        }
        assertEquals(0, depth, "unbalanced braces");
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        int from = source.indexOf(needle);
        while (from >= 0) {
            count++;
            from = source.indexOf(needle, from + needle.length());
        }
        return count;
    }
}
