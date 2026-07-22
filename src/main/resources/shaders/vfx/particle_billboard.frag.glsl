#version 430 core

in vec2 particleCorner;
in vec4 particleColor;

out vec4 fragmentColor;

void main() {
    float distanceFromCenter = length(particleCorner);
    float falloff = smoothstep(1.0, 0.0, distanceFromCenter);
    float core = smoothstep(0.45, 0.0, distanceFromCenter) * 0.6;
    vec3 hdrColor = particleColor.rgb * 4.0 * (falloff + core);
    fragmentColor = vec4(hdrColor * particleColor.a, 1.0);
}
