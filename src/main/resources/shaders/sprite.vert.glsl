#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inUv;
layout(location = 2) in vec4 inColor;

out vec2 spriteUv;
out vec4 spriteColor;

void main() {
    spriteUv = inUv;
    spriteColor = inColor;
    gl_Position = frame.cameraViewProjection * vec4(inPosition, 0.0, 1.0);
}
