package fr.epistudio.epysia.editor.preview;

public final class ShaderPreviewReference {

    public static final String SURFACE_SOURCE = """
            void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, in vec2 uv, in float time) {
            }

            void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time) {
                vec2 cell = floor(uv * 8.0);
                float parity = mod(cell.x + cell.y, 2.0);
                vec3 checker = mix(vec3(0.14, 0.14, 0.16), vec3(0.86, 0.86, 0.88), parity);
                vec3 gradient = vec3(uv.x, uv.y, 1.0 - uv.x);
                emissive = mix(checker, gradient, 0.55);
                albedoColor = vec4(0.0, 0.0, 0.0, 1.0);
                metallic = 1.0;
                roughness = 1.0;
            }
            """;

    private ShaderPreviewReference() {
    }
}
