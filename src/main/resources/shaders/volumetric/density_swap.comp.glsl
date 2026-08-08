#version 430 core
#include "volumetric/volumetric_common.glsl"

layout(local_size_x = 128) in;

layout(std430, binding = 4) buffer DensityBuffer {
    int density[];
};

layout(std430, binding = 5) readonly buffer PingBuffer {
    int ping[];
};

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(VOXEL_COUNT)) {
        return;
    }
    density[index] = ping[index];
}
