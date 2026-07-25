#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D inputColor;

layout(std140, binding = 1) uniform PostUbo {
    vec4 vignetteParams;
    vec4 gradeParams;
    vec4 fogColor;
    vec4 fogDistance;
    vec4 cameraDepth;
    vec4 cameraPosition;
    mat4 inverseViewProjection;
    vec4 effectParams;
} post;

out vec4 outColor;

void main() {
    outColor = texture(inputColor, vertexUv);
}
