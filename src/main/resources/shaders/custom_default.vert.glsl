#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;
layout(location = 3) in vec3 inTangent;

out vec3 vWorldNormal;
out vec2 vUv;

invariant gl_Position;

void main() {
    vec4 world = OBJECT_MODEL * vec4(inPosition, 1.0);
    vWorldNormal = normalize(mat3(OBJECT_MODEL) * inNormal);
    vUv = inUv;
    gl_Position = frame.cameraViewProjection * world;
}
