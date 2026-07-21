#version 430 core
#include "vfx/particle_common.glsl"

layout(local_size_x = 64) in;

void main() {
    uint invocation = gl_GlobalInvocationID.x;
    if (invocation >= effect.spawnSeedPool.x) {
        return;
    }
    int previousTop = atomicAdd(freeTop, -1);
    if (previousTop <= 0) {
        atomicAdd(freeTop, 1);
        return;
    }
    uint slot = freeEntries[previousTop - 1];
    uint spawnKey = effect.spawnSeedPool.y * 9781u + effect.spawnSeedPool.z + invocation;
    float lifetime = 1.5 + hashFloat(spawnKey * 3u + 1u);
    float angle = hashFloat(spawnKey * 3u + 2u) * 6.2831853;
    float spread = hashFloat(spawnKey * 3u) * 0.9;
    vec3 direction = normalize(vec3(cos(angle) * spread, 2.2, sin(angle) * spread));
    particles[slot].positionAge = vec4(effect.emitterPositionDelta.xyz, 0.0);
    particles[slot].velocityLifetime = vec4(direction * (2.0 + hashFloat(spawnKey * 7u)), lifetime);
    particles[slot].color = vec4(1.0, 0.45, 0.12, 1.0);
    particles[slot].sizeRotation = vec4(0.08, 0.0, 0.0, 0.0);
    particles[slot].seedUser = vec4(hashFloat(spawnKey), 0.0, 0.0, 0.0);
    particles[slot].userExtra = vec4(0.0);
}
