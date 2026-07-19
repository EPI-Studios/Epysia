#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;
layout(location = 3) in vec3 inTangent;

out vec3 vertexWorldPosition;
out vec3 vertexWorldNormal;
out vec3 vertexWorldTangent;
out vec2 vertexUv;
out float vertexViewDepth;

void main() {
    vec4 worldPosition = object.model * vec4(inPosition, 1.0);
    mat3 normalMatrix = mat3(object.normalMatrix);
    vertexWorldPosition = worldPosition.xyz;
    vertexWorldNormal = normalize(normalMatrix * inNormal);
    vertexWorldTangent = normalize(normalMatrix * inTangent);
    vertexUv = inUv;
    gl_Position = frame.cameraViewProjection * worldPosition;
    vertexViewDepth = gl_Position.w;
}
