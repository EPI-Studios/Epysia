#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;

layout(std140, binding = 2) uniform CascadeUbo {
    ivec4 index;
} cascade;

out vec2 vertexUv;

// SURFACE_FUNCTIONS

invariant gl_Position;

void main() {
    vertexUv = inUv;
    vec4 worldPosition = OBJECT_MODEL * vec4(inPosition, 1.0);
    // SURFACE_VERTEX_CALL
    gl_Position = frame.cascadeViewProjection[cascade.index.x] * worldPosition;
}
