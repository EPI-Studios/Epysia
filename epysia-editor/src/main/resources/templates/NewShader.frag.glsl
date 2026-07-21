#version 430 core
#include "lib/frame_uniforms.glsl"

// Raw fragment shader: you own the whole lighting model here.
// For PBR, textures, shadows and IBL, write a surface shader instead.

in vec3 vWorldNormal;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 lightDirection = normalize(vec3(0.4, 0.8, 0.4));
    float shading = max(dot(normalize(vWorldNormal), lightDirection), 0.0);
    vec3 albedo = vec3(0.85, 0.35, 0.60);
    fragColor = vec4(albedo * (frame.ambientColor.rgb * frame.ambientColor.a + vec3(shading)), 1.0);
}
