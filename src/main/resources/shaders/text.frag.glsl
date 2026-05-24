#version 430 core

in vec2 vertexUv;

layout(std140, binding = 0) uniform TextUbo {
    vec4 viewportSize;
    vec4 textColor;
} text;

layout(binding = 1) uniform sampler2D atlasTexture;

out vec4 outColor;

void main() {
    float coverage = texture(atlasTexture, vertexUv).a;
    outColor = vec4(text.textColor.rgb, text.textColor.a * coverage);
}
