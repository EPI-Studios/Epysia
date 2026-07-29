#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inUv;
layout(location = 2) in vec4 inColor;
layout(location = 3) in vec4 inMaterial;
layout(location = 4) in vec4 inSurface;
layout(location = 5) in vec4 inParams0;
layout(location = 6) in vec4 inParams1;

out vec2 spriteUv;
out vec4 spriteColor;
out vec4 spriteMaterial;
out vec4 spriteSurfaceData;
out vec2 spriteWorldPosition;
out vec4 spriteParams0;
out vec4 spriteParams1;

void main() {
    spriteUv = inUv;
    spriteColor = inColor;
    spriteMaterial = inMaterial;
    spriteSurfaceData = inSurface;
    spriteWorldPosition = inPosition;
    spriteParams0 = inParams0;
    spriteParams1 = inParams1;
    gl_Position = frame.cameraViewProjection * vec4(inPosition, 0.0, 1.0);
}
