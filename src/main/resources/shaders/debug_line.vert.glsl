#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec4 inColour;

out vec4 lineColour;

void main() {
    lineColour = inColour;
    gl_Position = frame.cameraViewProjection * vec4(inPosition, 1.0);
}
