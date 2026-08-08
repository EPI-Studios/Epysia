#version 430 core

in vec2 vertexUv;

layout(std140, binding = 0) uniform SkyUbo {
    mat4 inverseViewProjection;
    vec4 cameraPosition;
    vec4 sunDirectionAndIntensity;
    vec4 sceneTime;
} sky;

// SKY_BODY

out vec4 outColor;

void main() {
    vec4 clipPosition = vec4(vertexUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 worldPosition = sky.inverseViewProjection * clipPosition;
    vec3 direction = normalize(worldPosition.xyz / worldPosition.w - sky.cameraPosition.xyz);
    vec3 radiance = skyRadiance(direction, sky.sunDirectionAndIntensity.xyz, sky.sunDirectionAndIntensity.w);
    outColor = vec4(radiance, 1.0);
}
