#version 430 core
#include "volumetric/volumetric_common.glsl"

layout(local_size_x = 1) in;

layout(std430, binding = 4) buffer DensityBuffer {
    int density[];
};

void main() {
    vec3 voxelSpace = worldToVoxelSpace(seedPoint.xyz);
    uvec3 resolution = uvec3(voxelResolution.xyz);
    if (any(lessThan(voxelSpace, vec3(0.0))) || any(greaterThanEqual(voxelSpace, vec3(resolution)))) {
        return;
    }
    density[toVoxelIndex(uvec3(voxelSpace))] = int(PROPAGATION_DISTANCE);
}
