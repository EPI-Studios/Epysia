#version 430 core

in vec2 vertexUv;
in vec3 vertexWorldPosition;
in vec3 vertexWorldNormal;

layout(std140, binding = 3) uniform MaskedMaterialUbo {
    vec3 baseColor;
    float metallic;
    float roughness;
    float emissiveStrength;
    float alphaCutoff;
    float normalScale;
    float occlusionStrength;
    vec4 lightmapScaleOffset;
    float lightmapStrength;
    float lightmapRgbmRange;
    int uvMapping;
    float triplanarSharpness;
    vec3 uvScale;
    vec3 uvOffset;
} material;

layout(binding = 0) uniform sampler2D albedo;

#include "lib/uv_mapping.glsl"

void main() {
    if (materialTexture(albedo, vertexUv).a < material.alphaCutoff) {
        discard;
    }
}
