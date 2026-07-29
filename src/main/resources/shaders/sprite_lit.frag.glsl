#version 430 core
#include "lib/frame_uniforms.glsl"
#include "lib/pbr.glsl"
#include "lib/lighting2d.glsl"

in vec2 spriteUv;
in vec4 spriteColor;
in vec4 spriteMaterial;
in vec4 spriteSurfaceData;
in vec2 spriteWorldPosition;
in vec4 spriteParams0;
in vec4 spriteParams1;

layout(binding = 1) uniform sampler2D spriteTexture;
layout(binding = 2) uniform sampler2D spriteNormalMap;
layout(binding = 3) uniform sampler2D spriteMetallicRoughnessMap;
layout(binding = 4) uniform sampler2D spriteEmissiveMap;

out vec4 outColor;

// SURFACE_FUNCTIONS

vec3 surfaceNormal(float normalStrength, float tangentSignX, float tangentSignY) {
    vec3 sampled = texture(spriteNormalMap, spriteUv).xyz * 2.0 - 1.0;
    sampled.xy *= normalStrength;
    vec3 tangent = vec3(tangentSignX, 0.0, 0.0);
    vec3 bitangent = vec3(0.0, tangentSignY, 0.0);
    vec3 planeNormal = vec3(0.0, 0.0, 1.0);
    return normalize(mat3(tangent, bitangent, planeNormal) * sampled);
}

void main() {
    vec4 base = texture(spriteTexture, spriteUv) * spriteColor;
    // SPRITE_SURFACE_CALL
    if (base.a <= 0.0) {
        discard;
    }
    float metallic = clamp(spriteMaterial.x * texture(spriteMetallicRoughnessMap, spriteUv).b, 0.0, 1.0);
    float roughness = clamp(spriteMaterial.y * texture(spriteMetallicRoughnessMap, spriteUv).g, 0.04, 1.0);
    vec3 normal = surfaceNormal(spriteMaterial.z, spriteSurfaceData.x, spriteSurfaceData.y);
    vec3 viewDirection = vec3(0.0, 0.0, 1.0);
    vec3 surfacePosition = vec3(spriteWorldPosition, 0.0);
    int surfaceLayers = int(spriteSurfaceData.z);
    int lightCount = light2dCount();

    vec3 accumulated = vec3(0.0);
    for (int i = 0; i < lightCount; i++) {
        Light2d light = lights2d[i];
        if (!light2dAffects(light, surfaceLayers)) {
            continue;
        }
        vec3 toLight;
        vec3 radiance;
        if (!unpackLight2d(light, surfacePosition, toLight, radiance)) {
            continue;
        }
        if (light.typeAndLayers.x == LIGHT2D_TYPE_GLOBAL) {
            accumulated += base.rgb * light.shapeParameters.x * light.colorAndIntensity.rgb;
        }
        accumulated += cookTorranceBrdf(normal, viewDirection, toLight,
                base.rgb, metallic, roughness, radiance);
    }
    vec3 emissive = texture(spriteEmissiveMap, spriteUv).rgb * spriteMaterial.w;
    outColor = vec4(accumulated + emissive, base.a);
}
