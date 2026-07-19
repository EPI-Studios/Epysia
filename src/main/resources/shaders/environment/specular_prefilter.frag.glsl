#version 430 core
#include "environment/capture_common.glsl"

layout(binding = 1) uniform samplerCube environmentMap;

in vec2 vertexUv;
out vec4 outColor;

const uint SAMPLE_COUNT = 96u;

void main() {
    vec3 normal = captureDirection(vertexUv);
    vec3 viewDirection = normal;
    float roughness = capture.params.x;

    vec3 accumulated = vec3(0.0);
    float totalWeight = 0.0;
    for (uint i = 0u; i < SAMPLE_COUNT; i++) {
        vec2 randomPair = hammersley(i, SAMPLE_COUNT);
        vec3 halfway = importanceSampleGgx(randomPair, normal, roughness);
        vec3 lightDirection = normalize(2.0 * dot(viewDirection, halfway) * halfway - viewDirection);
        float normalDotLight = max(dot(normal, lightDirection), 0.0);
        if (normalDotLight > 0.0) {
            accumulated += texture(environmentMap, lightDirection).rgb * normalDotLight;
            totalWeight += normalDotLight;
        }
    }
    outColor = vec4(accumulated / max(totalWeight, 1.0e-4), 1.0);
}
