#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inTexCoord;
layout(location = 2) in vec4 inColour;
layout(location = 3) in float inOutline;

out vec2 textTexCoord;
out vec4 textColour;
out float textOutline;

void main() {
    textTexCoord = inTexCoord;
    textColour = inColour;
    textOutline = inOutline;
    gl_Position = frame.cameraViewProjection * vec4(inPosition, 1.0);
}
