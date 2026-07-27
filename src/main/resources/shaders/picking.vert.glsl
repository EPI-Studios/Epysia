#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;

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

invariant gl_Position;

void main() {
    vec3 localPosition = inPosition;
#ifdef SKINNED
    localPosition = skinPosition(inPosition);
#endif
    gl_Position = frame.cameraViewProjection * object.model * vec4(localPosition, 1.0);
}
