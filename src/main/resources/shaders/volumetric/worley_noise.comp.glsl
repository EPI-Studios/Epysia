#version 430 core

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;

layout(r16f, binding = 1) uniform writeonly image3D noiseVolume;

layout(std140, binding = 0) uniform NoiseUniforms {
    vec4 cellParameters;
    vec4 shapeParameters;
};

#define NOISE_SEED int(cellParameters.x)
#define NOISE_OCTAVES int(cellParameters.y)
#define NOISE_CELL_SIZE cellParameters.z
#define NOISE_AXIS_CELLS cellParameters.w

#define NOISE_AMPLITUDE shapeParameters.x
#define NOISE_WARP shapeParameters.y
#define NOISE_BIAS shapeParameters.z
#define NOISE_INVERTED (shapeParameters.w > 0.5)

float hashInteger(uint value) {
    value = (value << 13u) ^ value;
    value = value * (value * value * 15731u + 0x789221u) + 0x76312589u;
    return float(value & uint(0x7fffffffu)) / float(0x7fffffff);
}

vec3 featurePoint(int cellIndex) {
    return vec3(hashInteger(uint(NOISE_SEED + cellIndex)),
                hashInteger(uint(NOISE_SEED + cellIndex * 2)),
                hashInteger(uint(NOISE_SEED + cellIndex * 3)));
}

int wrappedCellIndex(ivec3 coordinate, int axisCellCount) {
    ivec3 wrapped = (coordinate + axisCellCount) % axisCellCount;
    return wrapped.x + axisCellCount * (wrapped.y + wrapped.z * axisCellCount);
}

float worley(vec3 coordinate, float cellSize, int axisCellCount) {
    ivec3 cell = ivec3(floor(coordinate / cellSize));
    vec3 localPosition = coordinate / cellSize - vec3(cell);
    float nearest = 1.0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            for (int z = -1; z <= 1; ++z) {
                ivec3 neighbour = cell + ivec3(x, y, z);
                vec3 offset = vec3(neighbour) + featurePoint(wrappedCellIndex(neighbour, axisCellCount));
                nearest = min(nearest, distance(vec3(cell) + localPosition, offset));
            }
        }
    }
    float shaped = sqrt(max(0.0, 1.0 - nearest * nearest));
    return pow(shaped, 6.0);
}

void main() {
    vec3 position = vec3(gl_GlobalInvocationID.xyz);
    float accumulated = 0.0;
    float gain = exp2(-1.0);
    float frequency = 1.0;
    float amplitude = 1.0;
    for (int octave = 0; octave < NOISE_OCTAVES; ++octave) {
        int axisCells = max(1, int(NOISE_AXIS_CELLS * frequency));
        accumulated += worley(position * frequency + float(octave) * NOISE_WARP,
                              NOISE_CELL_SIZE, axisCells) * amplitude;
        frequency *= 2.0;
        amplitude *= gain;
    }
    accumulated = clamp(accumulated + NOISE_BIAS, 0.0, 1.0) * NOISE_AMPLITUDE;
    float value = NOISE_INVERTED ? NOISE_AMPLITUDE - accumulated : accumulated;
    imageStore(noiseVolume, ivec3(gl_GlobalInvocationID.xyz), vec4(value, 0.0, 0.0, 0.0));
}
