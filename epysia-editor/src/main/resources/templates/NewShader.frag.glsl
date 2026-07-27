#version 430 core
#include "lib/frame_uniforms.glsl"


uniform vec3 tint; // @color @default 0.85, 0.35, 0.60

in vec3 vWorldNormal;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 lightDirection = normalize(vec3(0.4, 0.8, 0.4));
    float shading = max(dot(normalize(vWorldNormal), lightDirection), 0.0);
    fragColor = vec4(tint * (frame.ambientColor.rgb * frame.ambientColor.a + vec3(shading)), 1.0);
}
