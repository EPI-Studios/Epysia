#version 430 core
#include "lib/frame_uniforms.glsl"
#include "vfx/particle_common.glsl"
#include "vfx/particle_depth_fade.glsl"

in vec2 particleCorner;
in vec4 particleColor;

out vec4 fragmentColor;

void main() {
    float distanceFromCenter = length(particleCorner);
    float falloff = smoothstep(1.0, 0.0, distanceFromCenter);
    float core = smoothstep(0.45, 0.0, distanceFromCenter) * 0.6;
    vec3 hdrColor = particleColor.rgb * 4.0 * (falloff + core);
    fragmentColor = vec4(hdrColor * particleColor.a * particleDepthFade(), 1.0);
}
