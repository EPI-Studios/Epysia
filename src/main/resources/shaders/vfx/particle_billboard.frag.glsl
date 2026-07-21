#version 430 core

in vec2 particleCorner;
in vec4 particleColor;

out vec4 fragmentColor;

void main() {
    float distanceFromCenter = length(particleCorner);
    float falloff = clamp(1.0 - distanceFromCenter, 0.0, 1.0);
    falloff *= falloff;
    fragmentColor = vec4(particleColor.rgb * particleColor.a * falloff, 1.0);
}
