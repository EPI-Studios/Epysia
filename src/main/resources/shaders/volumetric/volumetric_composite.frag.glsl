#version 430 core

in vec2 vertexUv;
out vec4 fragmentColor;

layout(binding = 0) uniform sampler2D scatteredColor;
layout(binding = 1) uniform sampler2D transmittance;
layout(binding = 2) uniform sampler2D sceneDepth;

layout(std140, binding = 3) uniform CompositeUniforms {
    vec4 compositeParameters;
};

#define SHARPNESS compositeParameters.x
#define BICUBIC (compositeParameters.y > 0.5)
#define DEBUG_VIEW int(compositeParameters.z)

vec4 cubicWeights(float fraction) {
    float squared = fraction * fraction;
    float cubed = squared * fraction;
    return vec4(
        (-cubed + 3.0 * squared - 3.0 * fraction + 1.0) / 6.0,
        (3.0 * cubed - 6.0 * squared + 4.0) / 6.0,
        (-3.0 * cubed + 3.0 * squared + 3.0 * fraction + 1.0) / 6.0,
        cubed / 6.0);
}

vec4 sampleBicubic(sampler2D source, vec2 uv) {
    vec2 resolution = vec2(textureSize(source, 0));
    vec2 texel = 1.0 / resolution;
    vec2 coordinate = uv * resolution - 0.5;
    vec2 fraction = fract(coordinate);
    vec2 base = floor(coordinate);

    vec4 weightsX = cubicWeights(fraction.x);
    vec4 weightsY = cubicWeights(fraction.y);

    vec4 accumulated = vec4(0.0);
    for (int y = 0; y < 4; ++y) {
        vec4 row = vec4(0.0);
        for (int x = 0; x < 4; ++x) {
            vec2 offset = (base + vec2(float(x) - 1.0, float(y) - 1.0) + 0.5) * texel;
            row += texture(source, offset) * weightsX[x];
        }
        accumulated += row * weightsY[y];
    }
    return accumulated;
}

vec4 sampleSmoke(vec2 uv) {
    return BICUBIC ? sampleBicubic(scatteredColor, uv) : texture(scatteredColor, uv);
}

vec4 sharpenedSmoke(vec2 uv) {
    vec4 center = sampleSmoke(uv);
    if (SHARPNESS == 0.0) {
        return center;
    }
    vec2 texel = 1.0 / vec2(textureSize(scatteredColor, 0));
    float neighbourWeight = -SHARPNESS;
    float centerWeight = SHARPNESS * 4.0 + 1.0;
    vec4 accumulated = center * centerWeight;
    accumulated += sampleSmoke(uv + texel * vec2(0.0, 1.0)) * neighbourWeight;
    accumulated += sampleSmoke(uv + texel * vec2(1.0, 0.0)) * neighbourWeight;
    accumulated += sampleSmoke(uv + texel * vec2(0.0, -1.0)) * neighbourWeight;
    accumulated += sampleSmoke(uv + texel * vec2(-1.0, 0.0)) * neighbourWeight;
    return accumulated;
}

void main() {
    float mask = clamp(texture(transmittance, vertexUv).r, 0.0, 1.0);
    vec4 smoke = sharpenedSmoke(vertexUv);

    if (DEBUG_VIEW == 1) {
        fragmentColor = vec4(max(smoke.rgb, vec3(0.0)), 1.0);
        return;
    }
    if (DEBUG_VIEW == 2) {
        fragmentColor = vec4(vec3(1.0 - mask), 1.0);
        return;
    }
    if (DEBUG_VIEW == 3) {
        fragmentColor = vec4(vec3(texture(sceneDepth, vertexUv).r), 1.0);
        return;
    }

    fragmentColor = vec4(max(smoke.rgb, vec3(0.0)), 1.0 - mask);
}
