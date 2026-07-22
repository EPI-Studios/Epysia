struct Particle {
    vec4 positionAge;
    vec4 velocityLifetime;
    vec4 color;
    vec4 sizeRotation;
    vec4 seedUser;
    vec4 userExtra;
};

layout(std430, binding = 5) buffer ParticlePool {
    Particle particles[];
};

layout(std430, binding = 6) buffer AliveList {
    uint aliveIndices[];
};

layout(std430, binding = 7) buffer FreeList {
    int freeTop;
    uint freeEntries[];
};

layout(std430, binding = 8) buffer IndirectCommand {
    uint indexCount;
    uint instanceCount;
    uint firstIndex;
    uint baseVertex;
    uint baseInstance;
};

layout(std430, binding = 9) buffer CurveLut {
    float curveSamples[];
};

layout(std430, binding = 10) buffer GradientLut {
    vec4 gradientSamples[];
};

layout(std140, binding = 1) uniform EffectUbo {
    vec4 emitterPositionDelta;
    uvec4 spawnSeedPool;
    vec4 effectClock;
    vec4 emitterMotion;
} effect;

const int VFX_LUT_RESOLUTION = 256;

float sampleCurve(int index, float t) {
    if (index < 0) {
        return 0.0;
    }
    float position = clamp(t, 0.0, 1.0) * float(VFX_LUT_RESOLUTION - 1);
    int lower = int(floor(position));
    int upper = min(lower + 1, VFX_LUT_RESOLUTION - 1);
    int base = index * VFX_LUT_RESOLUTION;
    return mix(curveSamples[base + lower], curveSamples[base + upper], position - float(lower));
}

vec4 sampleGradient(int index, float t) {
    if (index < 0) {
        return vec4(1.0);
    }
    float position = clamp(t, 0.0, 1.0) * float(VFX_LUT_RESOLUTION - 1);
    int lower = int(floor(position));
    int upper = min(lower + 1, VFX_LUT_RESOLUTION - 1);
    int base = index * VFX_LUT_RESOLUTION;
    return mix(gradientSamples[base + lower], gradientSamples[base + upper], position - float(lower));
}

float effectNormalizedTime() {
    return effect.effectClock.x;
}

vec3 simulationSpaceOffset() {
    return effect.emitterMotion.xyz * effect.effectClock.w;
}

uint hashUint(uint value) {
    uint state = value * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float hashFloat(uint value) {
    return float(hashUint(value) & 0x00FFFFFFu) / 16777215.0;
}
