#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inUv;
layout(location = 2) in vec4 inColor;
layout(location = 5) in vec4 inParams0;
layout(location = 6) in vec4 inParams1;

#include "lib/object2d.glsl"

out vec2 spriteUv;
out vec4 spriteColor;
out vec2 spriteWorldPosition;
out vec4 spriteParams0;
out vec4 spriteParams1;

void main() {
    vec2 worldPosition = objectToWorld2d(inPosition);
    spriteUv = inUv;
    spriteColor = inColor;
    spriteWorldPosition = worldPosition;
    spriteParams0 = inParams0;
    spriteParams1 = inParams1;
    gl_Position = frame.cameraViewProjection * vec4(worldPosition, 0.0, 1.0);
}
