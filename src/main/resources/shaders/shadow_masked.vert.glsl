#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 2) in vec2 inUv;

layout(std140, binding = 2) uniform CascadeUbo {
    ivec4 index;
} cascade;

out vec2 vertexUv;

void main() {
    vertexUv = inUv;
    gl_Position = frame.cascadeViewProjection[cascade.index.x] * object.model * vec4(inPosition, 1.0);
}
