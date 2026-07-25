#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;

layout(std140, binding = 2) uniform CascadeUbo {
    ivec4 index;
} cascade;

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
#endif

int surfaceInstanceIndex;

// SURFACE_FUNCTIONS

invariant gl_Position;

void main() {
    vec3 localPosition = inPosition;
#ifdef SKINNED
    localPosition = skinPosition(inPosition);
#endif
    vec4 worldPosition = OBJECT_MODEL * vec4(localPosition, 1.0);
    surfaceInstanceIndex = OBJECT_INSTANCE_INDEX;
    // SURFACE_VERTEX_CALL
    gl_Position = frame.spotShadowViewProjection[cascade.index.x] * worldPosition;
}
