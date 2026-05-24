#version 430 core
in vec2 vertexUv;
in vec4 vertexColor;

layout(binding = 1) uniform sampler2D atlasTexture;

out vec4 outColor;

void main() {
    float coverage = texture(atlasTexture, vertexUv).a;
    outColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
