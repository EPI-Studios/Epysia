#version 430 core
#include "lib/frame_uniforms.glsl"

// Raw vertex shader: replaces the lit pipeline entirely, no PBR, no shadows.
// Use OBJECT_MODEL and OBJECT_NORMAL_MATRIX rather than object.model, so the
// material keeps working when the renderer batches it with GPU instancing.

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;
layout(location = 3) in vec3 inTangent;

out vec3 vWorldNormal;
out vec2 vUv;

void main() {
    vec4 world = OBJECT_MODEL * vec4(inPosition, 1.0);
    vWorldNormal = normalize(mat3(OBJECT_MODEL) * inNormal);
    vUv = inUv;
    gl_Position = frame.cameraViewProjection * world;
}
