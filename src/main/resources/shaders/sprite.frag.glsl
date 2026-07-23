#version 430 core

in vec2 spriteUv;
in vec4 spriteColor;

layout(binding = 1) uniform sampler2D spriteTexture;

out vec4 outColor;

void main() {
    outColor = texture(spriteTexture, spriteUv) * spriteColor;
}
