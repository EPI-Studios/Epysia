#version 430 core
#include "volumetric/volumetric_common.glsl"

layout(local_size_x = 128) in;

layout(std430, binding = 2) readonly buffer OccupancyBuffer {
    int occupancy[];
};

layout(std430, binding = 4) readonly buffer DensityBuffer {
    int density[];
};

layout(std430, binding = 5) buffer PingBuffer {
    int ping[];
};

const ivec3 NEIGHBOUR_OFFSETS[6] = ivec3[6](
    ivec3(1, 0, 0), ivec3(-1, 0, 0),
    ivec3(0, 1, 0), ivec3(0, -1, 0),
    ivec3(0, 0, 1), ivec3(0, 0, -1));

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(VOXEL_COUNT)) {
        return;
    }

    int current = density[index];
    if (current > 0) {
        ping[index] = current;
        return;
    }

    ivec3 voxelPosition = ivec3(toVoxelPosition(index));
    vec3 localSeed = (volumeWorldToLocal * vec4(seedPoint.xyz, 1.0)).xyz;
    vec3 fromSeed = voxelCenterLocal(uvec3(voxelPosition)) - localSeed;
    vec3 radius = max(growthRadius.xyz, vec3(0.0001));
    if (length(fromSeed / radius) > 1.0) {
        ping[index] = 0;
        return;
    }

    ivec3 resolution = ivec3(voxelResolution.xyz);
    int reach = 0;
    for (int offset = 0; offset < 6; ++offset) {
        ivec3 samplePosition = voxelPosition + NEIGHBOUR_OFFSETS[offset];
        if (any(lessThan(samplePosition, ivec3(0))) || any(greaterThanEqual(samplePosition, resolution))) {
            continue;
        }
        reach = max(reach, density[toVoxelIndex(uvec3(samplePosition))]);
    }

    if (occupancy[index] != 0 && reach > 1) {
        reach = 2;
    }
    ping[index] = max(0, reach - 1);
}
