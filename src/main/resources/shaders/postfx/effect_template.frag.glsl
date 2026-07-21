#version 430 core
in vec2 vertexUv;
out vec4 outColor;

layout(binding = 0) uniform sampler2D postEffectInputColor;
layout(binding = 1) uniform sampler2D sceneDepth;

layout(std140, binding = 2) uniform PostEffectFrame {
    float time;
    float nearPlane;
    vec2 resolution;
    vec3 cameraPosition;
    float farPlane;
    mat4 inverseViewProjection;
};

float sceneRawDepth(vec2 uv) {
    return texture(sceneDepth, uv).r;
}

bool sceneIsSky(vec2 uv) {
    return sceneRawDepth(uv) >= 1.0;
}

float sceneViewDepth(vec2 uv) {
    float clipZ = sceneRawDepth(uv) * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - clipZ * (farPlane - nearPlane));
}

vec3 sceneWorldPosition(vec2 uv) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, sceneRawDepth(uv) * 2.0 - 1.0, 1.0);
    vec4 worldPosition = inverseViewProjection * clipPosition;
    return worldPosition.xyz / worldPosition.w;
}

float sceneCameraDistance(vec2 uv) {
    return length(sceneWorldPosition(uv) - cameraPosition);
}

// POST_EFFECT_UNIFORMS

// POST_EFFECT_FUNCTIONS

void main() {
    outColor = postEffect(texture(postEffectInputColor, vertexUv), vertexUv);
}
