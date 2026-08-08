#version 430 core

layout(local_size_x = 64) in;

layout(std430, binding = 0) readonly buffer SourceVertices {
    float sourceVertices[];
};

layout(std430, binding = 1) readonly buffer JointPalette {
    vec4 jointRows[];
};

layout(std430, binding = 2) writeonly buffer DeformedVertices {
    float deformedVertices[];
};

layout(std140, binding = 3) uniform DeformParameters {
    ivec4 counts;
} parameters;

const uint POSITION_OFFSET = 0u;
const uint NORMAL_OFFSET = 3u;
const uint UV_OFFSET = 6u;
const uint TANGENT_OFFSET = 8u;
const uint TAIL_OFFSET = 11u;
const uint SKIN_FLOAT_COUNT = 6u;

uvec4 jointsAt(uint skinBase) {
    uint packedLow = floatBitsToUint(sourceVertices[skinBase]);
    uint packedHigh = floatBitsToUint(sourceVertices[skinBase + 1u]);
    return uvec4(packedLow & 0xFFFFu, packedLow >> 16u, packedHigh & 0xFFFFu, packedHigh >> 16u);
}

vec4 weightsAt(uint skinBase) {
    return vec4(sourceVertices[skinBase + 2u], sourceVertices[skinBase + 3u],
                sourceVertices[skinBase + 4u], sourceVertices[skinBase + 5u]);
}

vec3 readVector(uint base) {
    return vec3(sourceVertices[base], sourceVertices[base + 1u], sourceVertices[base + 2u]);
}

void writeVector(uint base, vec3 value) {
    deformedVertices[base] = value.x;
    deformedVertices[base + 1u] = value.y;
    deformedVertices[base + 2u] = value.z;
}

vec3 skinPosition(vec3 position, uvec4 joints, vec4 weights) {
    vec4 homogeneous = vec4(position, 1.0);
    vec3 skinned = vec3(0.0);
    for (int influence = 0; influence < 4; influence++) {
        uint base = joints[influence] * 3u;
        skinned += weights[influence] * vec3(dot(jointRows[base], homogeneous),
                                             dot(jointRows[base + 1u], homogeneous),
                                             dot(jointRows[base + 2u], homogeneous));
    }
    return skinned;
}

vec3 skinDirection(vec3 direction, uvec4 joints, vec4 weights) {
    vec3 skinned = vec3(0.0);
    for (int influence = 0; influence < 4; influence++) {
        uint base = joints[influence] * 3u;
        skinned += weights[influence] * vec3(dot(jointRows[base].xyz, direction),
                                             dot(jointRows[base + 1u].xyz, direction),
                                             dot(jointRows[base + 2u].xyz, direction));
    }
    return skinned;
}

void main() {
    uint vertexIndex = gl_GlobalInvocationID.x;
    if (vertexIndex >= uint(parameters.counts.x)) {
        return;
    }
    uint sourceStride = uint(parameters.counts.y);
    uint sourceBase = vertexIndex * sourceStride;
    uint deformedBase = vertexIndex * uint(parameters.counts.z);
    uint skinBase = sourceBase + sourceStride - SKIN_FLOAT_COUNT;
    uvec4 joints = jointsAt(skinBase);
    vec4 weights = weightsAt(skinBase);

    writeVector(deformedBase + POSITION_OFFSET,
            skinPosition(readVector(sourceBase + POSITION_OFFSET), joints, weights));
    writeVector(deformedBase + NORMAL_OFFSET,
            skinDirection(readVector(sourceBase + NORMAL_OFFSET), joints, weights));
    deformedVertices[deformedBase + UV_OFFSET] = sourceVertices[sourceBase + UV_OFFSET];
    deformedVertices[deformedBase + UV_OFFSET + 1u] = sourceVertices[sourceBase + UV_OFFSET + 1u];
    writeVector(deformedBase + TANGENT_OFFSET,
            skinDirection(readVector(sourceBase + TANGENT_OFFSET), joints, weights));
    for (uint tail = 0u; tail < uint(parameters.counts.w); tail++) {
        deformedVertices[deformedBase + TAIL_OFFSET + tail] = sourceVertices[sourceBase + TAIL_OFFSET + tail];
    }
}
