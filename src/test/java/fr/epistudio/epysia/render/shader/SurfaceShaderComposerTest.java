package fr.epistudio.epysia.render.shader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceShaderComposerTest {

    private static final String BASE = """
            #version 430 core
            // SURFACE_FUNCTIONS

            vec3 shadeLight() {
            #ifdef SURFACE_LIGHT_ENABLED
                return vec3(1.0);
            #else
                return vec3(0.0);
            #endif
            }

            void main() {
                // SURFACE_COLOR_CALL
            }
            """;

    private static LoadedShader base() {
        return new LoadedShader(BASE, List.of());
    }

    private static LoadedShader surface(String source) {
        return new LoadedShader(source, List.of());
    }

    @Test
    void enablesLightHookWhenSurfaceDeclaresIt() {
        LoadedShader composed = SurfaceShaderComposer.composeFragment(base(), surface("""
                void surfaceLight(inout vec3 lightColor, in vec3 worldNormal, in vec3 viewDirection,
                                  in vec3 toLight, in vec3 albedo, in float metallic, in float roughness,
                                  in vec3 radiance, in int lightType) {
                    lightColor = albedo;
                }
                """));
        assertTrue(composed.source().contains("#define SURFACE_LIGHT_ENABLED"));
        assertTrue(composed.source().contains("void surfaceLight("));
    }

    @Test
    void leavesEngineBrdfWhenSurfaceHasNoLightHook() {
        LoadedShader composed = SurfaceShaderComposer.composeFragment(base(), surface("""
                void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness,
                                  inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time) {
                    albedoColor.rgb *= 2.0;
                }
                """));
        assertFalse(composed.source().contains("#define SURFACE_LIGHT_ENABLED"));
        assertTrue(composed.source().contains("void surfaceColor("));
    }

    @Test
    void enablesUnshadedFromRenderMode() {
        LoadedShader composed = SurfaceShaderComposer.composeFragment(base(), surface("""
                render_mode unshaded;
                """));
        assertTrue(composed.source().contains("#define SURFACE_UNSHADED"));
    }

    @Test
    void keepsDefinesAheadOfEngineCodeThatReadsThem() {
        LoadedShader composed = SurfaceShaderComposer.composeFragment(base(), surface("""
                void surfaceLight(inout vec3 lightColor, in vec3 worldNormal, in vec3 viewDirection,
                                  in vec3 toLight, in vec3 albedo, in float metallic, in float roughness,
                                  in vec3 radiance, in int lightType) {
                    lightColor = albedo;
                }
                """));
        int defineIndex = composed.source().indexOf("#define SURFACE_LIGHT_ENABLED");
        int usageIndex = composed.source().indexOf("#ifdef SURFACE_LIGHT_ENABLED");
        assertTrue(defineIndex >= 0 && usageIndex > defineIndex);
    }
}
