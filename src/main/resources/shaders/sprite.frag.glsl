#version 430 core
#include "lib/frame_uniforms.glsl"

in vec2 spriteUv;
in vec4 spriteColor;
in vec2 spriteWorldPosition;
in vec4 spriteParams0;
in vec4 spriteParams1;

layout(binding = 1) uniform sampler2D spriteTexture;

out vec4 outColor;

// SURFACE_FUNCTIONS

void main() {
    vec4 base = texture(spriteTexture, spriteUv) * spriteColor;
    // SPRITE_SURFACE_CALL
    outColor = base;
}
