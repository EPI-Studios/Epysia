#version 430 core
in vec2 vertexUv;
in vec4 vertexColor;

layout(binding = 1) uniform sampler2D imageTexture;

out vec4 outColor;

void main() {
    vec4 sampled = texture(imageTexture, vertexUv);
    outColor = sampled * vertexColor;
}
