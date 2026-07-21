#version 430 core
#include "vfx/particle_common.glsl"

layout(local_size_x = 1) in;

void main() {
    indexCount = 6u;
    instanceCount = 0u;
    firstIndex = 0u;
    baseVertex = 0u;
    baseInstance = 0u;
}
