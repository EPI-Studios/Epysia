#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;
layout(location = 3) in vec3 inTangent;

#ifdef SKINNED
layout(location = 4) in uvec4 inJoints;
layout(location = 5) in vec4 inWeights;

layout(std430, binding = 4) readonly buffer JointPalette {
    vec4 jointRows[];
};

vec3 skinPosition(vec3 position) {
    vec4 homogeneous = vec4(position, 1.0);
    vec3 skinned = vec3(0.0);
    for (int influence = 0; influence < 4; influence++) {
        uint base = inJoints[influence] * 3u;
        float weight = inWeights[influence];
        skinned += weight * vec3(dot(jointRows[base], homogeneous),
                                 dot(jointRows[base + 1u], homogeneous),
                                 dot(jointRows[base + 2u], homogeneous));
    }
    return skinned;
}

vec3 skinDirection(vec3 direction) {
    vec3 skinned = vec3(0.0);
    for (int influence = 0; influence < 4; influence++) {
        uint base = inJoints[influence] * 3u;
        float weight = inWeights[influence];
        skinned += weight * vec3(dot(jointRows[base].xyz, direction),
                                 dot(jointRows[base + 1u].xyz, direction),
                                 dot(jointRows[base + 2u].xyz, direction));
    }
    return skinned;
}
#endif

out vec3 vertexWorldPosition;
out vec3 vertexWorldNormal;
out vec3 vertexWorldTangent;
out vec2 vertexUv;
out float vertexViewDepth;

// SURFACE_FUNCTIONS

invariant gl_Position;

void main() {
    vec3 localPosition = inPosition;
    vec3 localNormal = inNormal;
    vec3 localTangent = inTangent;
#ifdef SKINNED
    localPosition = skinPosition(inPosition);
    localNormal = skinDirection(inNormal);
    localTangent = skinDirection(inTangent);
#endif
    vec4 worldPosition = OBJECT_MODEL * vec4(localPosition, 1.0);
    mat3 normalMatrix = mat3(OBJECT_NORMAL_MATRIX);
    vertexWorldPosition = worldPosition.xyz;
    vertexWorldNormal = normalize(normalMatrix * localNormal);
    vertexWorldTangent = normalize(normalMatrix * localTangent);
    vertexUv = inUv;
    // SURFACE_VERTEX_CALL
    gl_Position = frame.cameraViewProjection * worldPosition;
    vertexViewDepth = gl_Position.w;
}
