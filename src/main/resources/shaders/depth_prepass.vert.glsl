#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;

out vec3 vertexWorldNormal;
flat out float vertexRenderLayer;

// SURFACE_FUNCTIONS

invariant gl_Position;

void main() {
    vec4 worldPosition = OBJECT_MODEL * vec4(inPosition, 1.0);
    // SURFACE_VERTEX_CALL
    vertexWorldNormal = normalize(mat3(OBJECT_NORMAL_MATRIX) * inNormal);
    vertexRenderLayer = OBJECT_NORMAL_MATRIX[3].w;
    gl_Position = frame.cameraViewProjection * worldPosition;
}
