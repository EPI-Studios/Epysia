layout(binding = 3) uniform sampler2DArrayShadow shadowCascades;
layout(binding = 12) uniform sampler2DArrayShadow spotShadowAtlas;
layout(binding = 13) uniform sampler2DArrayShadow pointShadowAtlas;

int selectShadowCascade(float viewDepth, int cascadeCount) {
    for (int i = 0; i < cascadeCount; i++) {
        if (viewDepth <= frame.cascadeSplits[i]) {
            return i;
        }
    }
    return -1;
}

#ifndef CASCADE_PCF_SAMPLES
#define CASCADE_PCF_SAMPLES 4
#endif
#ifndef CASCADE_PCF_FILTERED_CASCADES
#define CASCADE_PCF_FILTERED_CASCADES 2
#endif
#ifndef CASCADE_PCF_SPREAD
#define CASCADE_PCF_SPREAD 2.0
#endif
const float GOLDEN_ANGLE = 2.4;
const float TWO_PI = 6.2831853;

float interleavedGradientNoise(vec2 position) {
    const vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(magic.z * fract(dot(position, magic.xy)));
}

vec2 vogelDiskOffset(int index, int sampleCount, float rotation) {
    float radius = sqrt(float(index) + 0.5) / sqrt(float(sampleCount));
    float theta = float(index) * GOLDEN_ANGLE + rotation;
    return vec2(cos(theta), sin(theta)) * radius;
}

float sampleCascadePcf(vec2 baseUv, int cascade, float reference) {
    if (cascade >= CASCADE_PCF_FILTERED_CASCADES) {
        return texture(shadowCascades, vec4(baseUv, float(cascade), reference));
    }
    vec2 texelSize = 1.0 / vec2(textureSize(shadowCascades, 0).xy);
    float rotation = interleavedGradientNoise(gl_FragCoord.xy) * TWO_PI;
    float sum = 0.0;
    for (int i = 0; i < CASCADE_PCF_SAMPLES; i++) {
        vec2 offset = vogelDiskOffset(i, CASCADE_PCF_SAMPLES, rotation) * texelSize * CASCADE_PCF_SPREAD;
        sum += texture(shadowCascades, vec4(baseUv + offset, float(cascade), reference));
    }
    return sum / float(CASCADE_PCF_SAMPLES);
}

float sampleShadowFactor(vec3 worldPosition, vec3 worldNormal, vec3 toLight, float viewDepth) {
    int cascadeCount = frame.lightCountAndShadowIndex.z;
    if (cascadeCount == 0) {
        return 1.0;
    }
    int cascade = selectShadowCascade(viewDepth, cascadeCount);
    if (cascade < 0) {
        return 1.0;
    }
    float normalDotLight = max(dot(worldNormal, toLight), 0.0);
    float texelWorldSize = frame.cascadeTexelSizes[cascade];
    vec3 offsetPosition = worldPosition + worldNormal * texelWorldSize * (1.2 + 2.0 * (1.0 - normalDotLight));
    vec4 lightSpacePosition = frame.cascadeViewProjection[cascade] * vec4(offsetPosition, 1.0);
    vec3 projected = lightSpacePosition.xyz / lightSpacePosition.w * 0.5 + 0.5;
    if (projected.z > 1.0 || any(lessThan(projected.xy, vec2(0.0))) || any(greaterThan(projected.xy, vec2(1.0)))) {
        return 1.0;
    }
    float slopeScale = clamp(1.0 - normalDotLight, 0.0, 1.0);
    float bias = 0.0006 + 0.0022 * slopeScale;
    return sampleCascadePcf(projected.xy, cascade, projected.z - bias);
}

float samplePcfSpot(vec2 baseUv, int layer, float reference) {
    vec2 texelSize = 1.0 / vec2(textureSize(spotShadowAtlas, 0).xy);
    float sum = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 offset = vec2(float(dx), float(dy)) * texelSize;
            sum += texture(spotShadowAtlas, vec4(baseUv + offset, float(layer), reference));
        }
    }
    return sum / 9.0;
}

float sampleSpotShadow(vec3 worldPosition, vec3 worldNormal, vec3 toLight, int layer) {
    float normalDotLight = max(dot(worldNormal, toLight), 0.0);
    vec3 offsetPosition = worldPosition + worldNormal * 0.03 * (1.0 + 2.0 * (1.0 - normalDotLight));
    vec4 lightSpacePosition = frame.spotShadowViewProjection[layer] * vec4(offsetPosition, 1.0);
    vec3 projected = lightSpacePosition.xyz / lightSpacePosition.w * 0.5 + 0.5;
    if (projected.z > 1.0 || any(lessThan(projected.xy, vec2(0.0))) || any(greaterThan(projected.xy, vec2(1.0)))) {
        return 1.0;
    }
    float bias = 0.0009 + 0.0025 * (1.0 - normalDotLight);
    return samplePcfSpot(projected.xy, layer, projected.z - bias);
}

int selectCubeFace(vec3 direction) {
    vec3 magnitude = abs(direction);
    if (magnitude.x >= magnitude.y && magnitude.x >= magnitude.z) {
        return direction.x > 0.0 ? 0 : 1;
    }
    if (magnitude.y >= magnitude.z) {
        return direction.y > 0.0 ? 2 : 3;
    }
    return direction.z > 0.0 ? 4 : 5;
}

float samplePcfPoint(vec2 baseUv, int layer, float reference) {
    vec2 texelSize = 1.0 / vec2(textureSize(pointShadowAtlas, 0).xy);
    float sum = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 offset = vec2(float(dx), float(dy)) * texelSize;
            sum += texture(pointShadowAtlas, vec4(baseUv + offset, float(layer), reference));
        }
    }
    return sum / 9.0;
}

float samplePointShadow(vec3 worldPosition, vec3 lightPosition, vec3 worldNormal, int pointIndex) {
    vec3 direction = worldPosition - lightPosition;
    int layer = pointIndex * 6 + selectCubeFace(direction);
    vec3 toLight = -normalize(direction);
    float normalDotLight = max(dot(worldNormal, toLight), 0.0);
    vec3 offsetPosition = worldPosition + worldNormal * 0.05 * (1.0 + 2.0 * (1.0 - normalDotLight));
    vec4 lightSpacePosition = frame.pointShadowViewProjection[layer] * vec4(offsetPosition, 1.0);
    vec3 projected = lightSpacePosition.xyz / lightSpacePosition.w * 0.5 + 0.5;
    if (projected.z > 1.0 || any(lessThan(projected.xy, vec2(0.0))) || any(greaterThan(projected.xy, vec2(1.0)))) {
        return 1.0;
    }
    float bias = 0.0012 + 0.003 * (1.0 - normalDotLight);
    return samplePcfPoint(projected.xy, layer, projected.z - bias);
}
