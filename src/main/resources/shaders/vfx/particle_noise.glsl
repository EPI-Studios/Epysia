const float NOISE_TWO_PI = 6.28318530718;
const float NOISE_GRADIENT_NORMALIZATION = 1.15470053838;
const float NOISE_CURL_EPSILON = 0.01;
const int NOISE_MAX_OCTAVES = 8;

uint noiseHashUint(uint value) {
    uint state = value * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

uint noiseHashCell(ivec3 cell) {
    uint mixedX = uint(cell.x + 65536) * 73856093u;
    uint mixedY = uint(cell.y + 65536) * 19349663u;
    uint mixedZ = uint(cell.z + 65536) * 83492791u;
    return noiseHashUint(mixedX ^ mixedY ^ mixedZ);
}

vec3 noiseGradient(ivec3 cell) {
    uint hashed = noiseHashCell(cell);
    float azimuth = float(hashed & 0xFFFFu) / 65535.0 * NOISE_TWO_PI;
    float polarCosine = float((hashed >> 16u) & 0xFFFFu) / 65535.0 * 2.0 - 1.0;
    float polarSine = sqrt(max(0.0, 1.0 - polarCosine * polarCosine));
    return vec3(polarSine * cos(azimuth), polarSine * sin(azimuth), polarCosine);
}

float noiseCornerContribution(ivec3 cell, ivec3 offset, vec3 fraction) {
    vec3 corner = vec3(offset);
    return dot(noiseGradient(cell + offset), fraction - corner);
}

float perlin3(vec3 point) {
    vec3 cellOrigin = floor(point);
    ivec3 cell = ivec3(cellOrigin);
    vec3 fraction = point - cellOrigin;
    vec3 fade = fraction * fraction * fraction * (fraction * (fraction * 6.0 - 15.0) + 10.0);
    float lowerBackLeft = noiseCornerContribution(cell, ivec3(0, 0, 0), fraction);
    float lowerBackRight = noiseCornerContribution(cell, ivec3(1, 0, 0), fraction);
    float upperBackLeft = noiseCornerContribution(cell, ivec3(0, 1, 0), fraction);
    float upperBackRight = noiseCornerContribution(cell, ivec3(1, 1, 0), fraction);
    float lowerFrontLeft = noiseCornerContribution(cell, ivec3(0, 0, 1), fraction);
    float lowerFrontRight = noiseCornerContribution(cell, ivec3(1, 0, 1), fraction);
    float upperFrontLeft = noiseCornerContribution(cell, ivec3(0, 1, 1), fraction);
    float upperFrontRight = noiseCornerContribution(cell, ivec3(1, 1, 1), fraction);
    float lowerBack = mix(lowerBackLeft, lowerBackRight, fade.x);
    float upperBack = mix(upperBackLeft, upperBackRight, fade.x);
    float lowerFront = mix(lowerFrontLeft, lowerFrontRight, fade.x);
    float upperFront = mix(upperFrontLeft, upperFrontRight, fade.x);
    float back = mix(lowerBack, upperBack, fade.y);
    float front = mix(lowerFront, upperFront, fade.y);
    return clamp(mix(back, front, fade.z) * NOISE_GRADIENT_NORMALIZATION, -1.0, 1.0);
}

float fbm3(vec3 point, int octaves) {
    int octaveCount = clamp(octaves, 1, NOISE_MAX_OCTAVES);
    float frequency = 1.0;
    float amplitude = 0.5;
    float total = 0.0;
    float normalization = 0.0;
    for (int octave = 0; octave < octaveCount; octave++) {
        total += perlin3(point * frequency) * amplitude;
        normalization += amplitude;
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    return normalization > 0.0 ? total / normalization : 0.0;
}

vec3 noisePotential(vec3 point) {
    return vec3(perlin3(point),
            perlin3(point + vec3(31.416, 47.853, 19.737)),
            perlin3(point + vec3(-53.219, 11.483, 67.291)));
}

vec3 curlNoise(vec3 point) {
    vec3 offsetX = vec3(NOISE_CURL_EPSILON, 0.0, 0.0);
    vec3 offsetY = vec3(0.0, NOISE_CURL_EPSILON, 0.0);
    vec3 offsetZ = vec3(0.0, 0.0, NOISE_CURL_EPSILON);
    float inverseSpan = 1.0 / (2.0 * NOISE_CURL_EPSILON);
    vec3 derivativeX = (noisePotential(point + offsetX) - noisePotential(point - offsetX)) * inverseSpan;
    vec3 derivativeY = (noisePotential(point + offsetY) - noisePotential(point - offsetY)) * inverseSpan;
    vec3 derivativeZ = (noisePotential(point + offsetZ) - noisePotential(point - offsetZ)) * inverseSpan;
    return vec3(derivativeY.z - derivativeZ.y,
            derivativeZ.x - derivativeX.z,
            derivativeX.y - derivativeY.x);
}
