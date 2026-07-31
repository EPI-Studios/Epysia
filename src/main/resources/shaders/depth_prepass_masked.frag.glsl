#version 430 core

in vec2 vertexUv;

layout(std140, binding = 3) uniform MaskedMaterialUbo {
    vec3 baseColor;
    float metallic;
    float roughness;
    float emissiveStrength;
    float alphaCutoff;
} material;

layout(binding = 0) uniform sampler2D albedo;

void main() {
    if (texture(albedo, vertexUv).a < material.alphaCutoff) {
        discard;
    }
}
