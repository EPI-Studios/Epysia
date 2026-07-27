#version 430 core
#include "environment/capture_common.glsl"
// SKY_BODY

in vec2 vertexUv;
out vec4 outColor;

void main() {
    vec3 direction = captureDirection(vertexUv);
    vec3 radiance = skyRadiance(direction,
            capture.sunDirectionAndIntensity.xyz, capture.sunDirectionAndIntensity.w);
    outColor = vec4(radiance, 1.0);
}
