#version 430 core
#include "vfx/particle_common.glsl"

layout(local_size_x = 64) in;

void main() {
    uint slot = gl_GlobalInvocationID.x;
    if (slot >= effect.spawnSeedPool.w) {
        return;
    }
    Particle particle = particles[slot];
    if (particle.velocityLifetime.w <= 0.0) {
        return;
    }
    float delta = effect.emitterPositionDelta.w;
    float age = particle.positionAge.w + delta;
    if (age >= particle.velocityLifetime.w) {
        particles[slot].velocityLifetime.w = 0.0;
        int previousTop = atomicAdd(freeTop, 1);
        freeEntries[previousTop] = slot;
        return;
    }
    vec3 velocity = particle.velocityLifetime.xyz + vec3(0.0, -4.0, 0.0) * delta;
    vec3 position = particle.positionAge.xyz + velocity * delta + simulationSpaceOffset();
    particles[slot].positionAge = vec4(position, age);
    particles[slot].velocityLifetime.xyz = velocity;
    float normalizedAge = age / particle.velocityLifetime.w;
    particles[slot].color.a = 1.0 - normalizedAge;
    uint drawIndex = atomicAdd(instanceCount, 1u);
    aliveIndices[drawIndex] = slot;
}
