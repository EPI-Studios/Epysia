struct Particle {
    vec4 positionAge;
    vec4 velocityLifetime;
    vec4 color;
    vec4 sizeRotation;
    vec4 seedUser;
    vec4 userExtra;
};

layout(std430, binding = 0) buffer ParticlePool {
    Particle particles[];
};

layout(std430, binding = 1) buffer AliveList {
    uint aliveIndices[];
};

layout(std430, binding = 2) buffer FreeList {
    int freeTop;
    uint freeEntries[];
};

layout(std430, binding = 3) buffer IndirectCommand {
    uint indexCount;
    uint instanceCount;
    uint firstIndex;
    uint baseVertex;
    uint baseInstance;
};

layout(std140, binding = 1) uniform EffectUbo {
    vec4 emitterPositionDelta;
    uvec4 spawnSeedPool;
} effect;

uint hashUint(uint value) {
    uint state = value * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float hashFloat(uint value) {
    return float(hashUint(value) & 0x00FFFFFFu) / 16777215.0;
}
