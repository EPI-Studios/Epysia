layout(binding = 3) uniform sampler2DArrayShadow shadowCascades;

int selectShadowCascade(float viewDepth, int cascadeCount) {
    for (int i = 0; i < cascadeCount; i++) {
        if (viewDepth <= frame.cascadeSplits[i]) {
            return i;
        }
    }
    return -1;
}

float samplePcf5x5(vec2 baseUv, int cascade, float reference) {
    vec2 texelSize = 1.0 / vec2(textureSize(shadowCascades, 0).xy);
    float sum = 0.0;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            vec2 offset = vec2(float(dx), float(dy)) * texelSize;
            sum += texture(shadowCascades, vec4(baseUv + offset, float(cascade), reference));
        }
    }
    return sum / 25.0;
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
    return samplePcf5x5(projected.xy, cascade, projected.z - bias);
}
