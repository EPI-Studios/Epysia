#version 430 core
#include "environment/capture_common.glsl"

in vec2 vertexUv;
out vec4 outColor;

const uint SAMPLE_COUNT = 512u;

float geometrySchlickIbl(float normalDotDirection, float roughness) {
    float k = (roughness * roughness) / 2.0;
    return normalDotDirection / (normalDotDirection * (1.0 - k) + k);
}

vec2 integrateBrdf(float normalDotView, float roughness) {
    vec3 viewDirection = vec3(sqrt(1.0 - normalDotView * normalDotView), 0.0, normalDotView);
    vec3 normal = vec3(0.0, 0.0, 1.0);
    float scale = 0.0;
    float bias = 0.0;
    for (uint i = 0u; i < SAMPLE_COUNT; i++) {
        vec2 randomPair = hammersley(i, SAMPLE_COUNT);
        vec3 halfway = importanceSampleGgx(randomPair, normal, roughness);
        vec3 lightDirection = normalize(2.0 * dot(viewDirection, halfway) * halfway - viewDirection);
        float normalDotLight = max(lightDirection.z, 0.0);
        if (normalDotLight > 0.0) {
            float normalDotHalf = max(halfway.z, 0.0);
            float viewDotHalf = max(dot(viewDirection, halfway), 0.0);
            float geometry = geometrySchlickIbl(normalDotView, roughness)
                    * geometrySchlickIbl(normalDotLight, roughness);
            float geometryVisibility = (geometry * viewDotHalf) / max(normalDotHalf * normalDotView, 1.0e-4);
            float fresnelWeight = pow(1.0 - viewDotHalf, 5.0);
            scale += (1.0 - fresnelWeight) * geometryVisibility;
            bias += fresnelWeight * geometryVisibility;
        }
    }
    return vec2(scale, bias) / float(SAMPLE_COUNT);
}

void main() {
    vec2 integrated = integrateBrdf(max(vertexUv.x, 1.0e-3), vertexUv.y);
    outColor = vec4(integrated, 0.0, 1.0);
}
