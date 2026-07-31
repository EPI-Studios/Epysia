package fr.epistudio.epysia.render.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SurfaceRenderModeTest {

    @Test
    void renderModeStatementsAreRemovedFromTheInjectedBody() {
        String body = """
                render_mode unshaded;

                void surfaceColor(inout vec4 albedoColor) {
                }
                """;

        String stripped = SurfaceShaderComposer.stripRenderModes(body);

        assertFalse(stripped.contains("render_mode"),
                "render_mode is not GLSL and breaks compilation once injected");
        assertTrue(stripped.contains("void surfaceColor"));
    }

    @Test
    void aCommentedRenderModeIsLeftAlone() {
        String body = "// render_mode unshaded;\nvoid surfaceColor() {\n}\n";

        assertEquals(body, SurfaceShaderComposer.stripRenderModes(body));
    }

    @Test
    void aBodyWithoutRenderModeIsReturnedUnchanged() {
        String body = "void surfaceColor() {\n}\n";

        assertEquals(body, SurfaceShaderComposer.stripRenderModes(body));
    }
}
