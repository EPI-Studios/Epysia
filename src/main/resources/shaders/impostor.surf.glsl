uniform sampler2D impostorAlbedoAtlas;  // @srgb
uniform sampler2D impostorNormalAtlas;  // @normal

uniform int impostorGridSize;           // @default 8
uniform float impostorRadius;           // @default 1.0
uniform vec3 impostorCenter;            // @default 0.0, 0.0, 0.0
uniform float impostorCoverageCutoff;   // @default 0.35

const float IMPOSTOR_POLE_THRESHOLD = 0.999;
const float IMPOSTOR_EPSILON = 1.0e-6;
const float IMPOSTOR_MINIMUM_WEIGHT = 1.0e-4;
const int IMPOSTOR_BLENDED_FRAMES = 3;

struct ImpostorFrames {
    vec2 cells[IMPOSTOR_BLENDED_FRAMES];
    vec3 weights;
};

struct ImpostorSample {
    vec4 atlasColor;
    vec3 atlasNormal;
};

vec3 impostorReferenceUp(vec3 direction) {
    return abs(direction.y) > IMPOSTOR_POLE_THRESHOLD ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
}

mat3 impostorBasis(vec3 direction) {
    vec3 right = normalize(cross(impostorReferenceUp(direction), direction));
    return mat3(right, cross(direction, right), direction);
}

vec3 impostorDecode(vec2 frameCoordinates) {
    float horizontal = frameCoordinates.x * 2.0 - 1.0;
    float vertical = frameCoordinates.y * 2.0 - 1.0;
    float alongX = (horizontal - vertical) * 0.5;
    float alongZ = (horizontal + vertical) * 0.5;
    return normalize(vec3(alongX, 1.0 - abs(alongX) - abs(alongZ), alongZ));
}

vec2 impostorEncode(vec3 direction) {
    float scale = max(abs(direction.x) + abs(direction.y) + abs(direction.z), IMPOSTOR_EPSILON);
    vec3 folded = direction / scale;
    return vec2((folded.x + folded.z) * 0.5 + 0.5, (folded.z - folded.x) * 0.5 + 0.5);
}

vec3 impostorCenterWorld() {
    return (objectToWorld() * vec4(impostorCenter, 1.0)).xyz;
}

vec3 impostorSafeScale() {
    return max(objectScale(), vec3(IMPOSTOR_EPSILON));
}

float impostorWorldRadius() {
    vec3 scale = impostorSafeScale();
    return impostorRadius * max(scale.x, max(scale.y, scale.z));
}

vec3 impostorToObjectSpace(vec3 worldOffset) {
    mat4 model = objectToWorld();
    vec3 scale = impostorSafeScale();
    return vec3(dot(worldOffset, model[0].xyz), dot(worldOffset, model[1].xyz),
                dot(worldOffset, model[2].xyz)) / (scale * scale);
}

vec3 impostorToWorldSpace(vec3 objectDirection) {
    mat4 model = objectToWorld();
    vec3 scale = impostorSafeScale();
    return model[0].xyz * (objectDirection.x / scale.x)
            + model[1].xyz * (objectDirection.y / scale.y)
            + model[2].xyz * (objectDirection.z / scale.z);
}

float impostorGridDivisor() {
    return float(max(impostorGridSize - 1, 1));
}

ImpostorFrames impostorFramesFor(vec3 direction) {
    float divisor = impostorGridDivisor();
    vec2 grid = clamp(impostorEncode(direction), vec2(0.0), vec2(1.0)) * divisor;
    vec2 base = floor(min(grid, vec2(divisor - 1.0)));
    vec2 fraction = grid - base;
    ImpostorFrames frames;
    if (fraction.x + fraction.y > 1.0) {
        frames.cells[0] = base + vec2(1.0, 0.0);
        frames.cells[1] = base + vec2(0.0, 1.0);
        frames.cells[2] = base + vec2(1.0, 1.0);
        frames.weights = vec3(1.0 - fraction.y, 1.0 - fraction.x, fraction.x + fraction.y - 1.0);
        return frames;
    }
    frames.cells[0] = base;
    frames.cells[1] = base + vec2(1.0, 0.0);
    frames.cells[2] = base + vec2(0.0, 1.0);
    frames.weights = vec3(1.0 - fraction.x - fraction.y, fraction.x, fraction.y);
    return frames;
}

vec2 impostorTileUv(vec2 cell, vec2 tileLocal) {
    float tiles = float(max(impostorGridSize, 1));
    vec2 inset = 0.5 / vec2(textureSize(impostorAlbedoAtlas, 0));
    vec2 lowerBound = cell / tiles + inset;
    vec2 upperBound = (cell + vec2(1.0)) / tiles - inset;
    return clamp((cell + tileLocal) / tiles, lowerBound, upperBound);
}

vec2 impostorTileLocal(vec2 cell, vec3 objectOffset) {
    mat3 basis = impostorBasis(impostorDecode(cell / impostorGridDivisor()));
    vec2 projected = vec2(dot(objectOffset, basis[0]), dot(objectOffset, basis[1]));
    return projected / max(impostorRadius, IMPOSTOR_EPSILON) * 0.5 + 0.5;
}

ImpostorSample impostorSampleFrame(vec2 cell, vec3 objectOffset) {
    ImpostorSample sampled;
    sampled.atlasColor = vec4(0.0);
    sampled.atlasNormal = vec3(0.0);
    vec2 tileLocal = impostorTileLocal(cell, objectOffset);
    if (any(lessThan(tileLocal, vec2(0.0))) || any(greaterThan(tileLocal, vec2(1.0)))) {
        return sampled;
    }
    vec2 atlasUv = impostorTileUv(cell, tileLocal);
    sampled.atlasColor = textureLod(impostorAlbedoAtlas, atlasUv, 0.0);
    sampled.atlasNormal = textureLod(impostorNormalAtlas, atlasUv, 0.0).xyz * 2.0 - 1.0;
    return sampled;
}

vec3 impostorSampleDirection(vec3 centerWorld) {
    vec3 viewObject = impostorToObjectSpace(frame.cameraPosition.xyz - centerWorld);
    vec3 upperHemisphere = vec3(viewObject.x, max(viewObject.y, 0.0), viewObject.z);
    return normalize(upperHemisphere + vec3(0.0, IMPOSTOR_MINIMUM_WEIGHT, 0.0));
}

void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, in vec2 uv, in float time) {
    vec3 centerWorld = impostorCenterWorld();
    vec3 toCamera = normalize(frame.cameraPosition.xyz - centerWorld);
    mat3 billboard = impostorBasis(toCamera);
    float side = impostorWorldRadius() * 2.0;
    worldPosition = centerWorld + (billboard[0] * localPosition.x + billboard[1] * localPosition.y) * side;
}

void surfaceShade(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec3 worldNormal, in vec3 viewDirection, in vec2 uv, in vec3 worldPosition, in float time) {
    vec3 centerWorld = impostorCenterWorld();
    vec3 objectOffset = impostorToObjectSpace(worldPosition - centerWorld);
    vec3 sampleDirection = impostorSampleDirection(centerWorld);
    ImpostorFrames frames = impostorFramesFor(sampleDirection);
    vec4 blendedColor = vec4(0.0);
    vec3 blendedNormal = vec3(0.0);
    float totalWeight = 0.0;
    for (int index = 0; index < IMPOSTOR_BLENDED_FRAMES; index++) {
        float weight = frames.weights[index];
        if (weight <= IMPOSTOR_MINIMUM_WEIGHT) {
            continue;
        }
        ImpostorSample sampled = impostorSampleFrame(frames.cells[index], objectOffset);
        blendedColor += sampled.atlasColor * weight;
        blendedNormal += sampled.atlasNormal * (weight * sampled.atlasColor.a);
        totalWeight += weight;
    }
    blendedColor /= max(totalWeight, IMPOSTOR_MINIMUM_WEIGHT);
    if (blendedColor.a < impostorCoverageCutoff) {
        discard;
    }
    albedoColor = vec4(blendedColor.rgb / max(blendedColor.a, IMPOSTOR_MINIMUM_WEIGHT), blendedColor.a);
    worldNormal = dot(blendedNormal, blendedNormal) > IMPOSTOR_EPSILON
            ? normalize(impostorToWorldSpace(normalize(blendedNormal)))
            : normalize(impostorToWorldSpace(sampleDirection));
}
