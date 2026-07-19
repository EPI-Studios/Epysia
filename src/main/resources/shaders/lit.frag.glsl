#version 430 core
#include "lib/frame_uniforms.glsl"
#include "lib/lighting.glsl"
#include "lib/pbr.glsl"
#include "lib/shadows.glsl"

in vec3 vertexWorldPosition;
in vec3 vertexWorldNormal;
in vec3 vertexWorldTangent;
in vec2 vertexUv;
in float vertexViewDepth;

layout(std140, binding = 2) uniform MaterialUbo {
    vec3 baseColor;
    float metallic;
    float roughness;
    float emissiveStrength;
    float alphaCutoff;
} material;

layout(binding = 4) uniform sampler2D albedo;
layout(binding = 5) uniform sampler2D normalMap;
layout(binding = 6) uniform sampler2D metallicRoughnessMap;
layout(binding = 7) uniform sampler2D occlusionMap;
layout(binding = 8) uniform sampler2D emissiveMap;
layout(binding = 9) uniform samplerCube irradianceMap;
layout(binding = 10) uniform samplerCube prefilteredMap;
layout(binding = 11) uniform sampler2D brdfLut;

const float MAX_REFLECTION_LOD = 4.0;

out vec4 outColor;

vec3 imageBasedAmbient(vec3 normal, vec3 viewDirection, vec3 albedoColor,
                       float metallic, float roughness, float occlusion) {
    float normalDotView = max(dot(normal, viewDirection), 1.0e-4);
    vec3 baseReflectivity = mix(vec3(0.04), albedoColor, metallic);
    vec3 fresnel = fresnelSchlickRoughness(normalDotView, baseReflectivity, roughness);
    vec3 diffuseWeight = (1.0 - fresnel) * (1.0 - metallic);
    vec3 irradianceSample = texture(irradianceMap, normal).rgb;
    vec3 reflected = reflect(-viewDirection, normal);
    vec3 prefilteredSample = textureLod(prefilteredMap, reflected, roughness * MAX_REFLECTION_LOD).rgb;
    vec2 environmentBrdf = texture(brdfLut, vec2(normalDotView, roughness)).rg;
    vec3 diffuse = diffuseWeight * irradianceSample * albedoColor;
    vec3 specular = prefilteredSample * (baseReflectivity * environmentBrdf.x + environmentBrdf.y);
    return (diffuse + specular) * occlusion * frame.ambientColor.rgb * frame.ambientColor.a;
}

vec3 computeWorldNormal() {
    vec3 sampledNormal = texture(normalMap, vertexUv).rgb * 2.0 - 1.0;
    vec3 normal = normalize(vertexWorldNormal) * (gl_FrontFacing ? 1.0 : -1.0);
    vec3 tangent = normalize(vertexWorldTangent - normal * dot(normal, vertexWorldTangent));
    vec3 bitangent = cross(normal, tangent);
    mat3 tangentToWorld = mat3(tangent, bitangent, normal);
    return normalize(tangentToWorld * sampledNormal);
}

void main() {
    vec4 albedoSample = texture(albedo, vertexUv);
    if (material.alphaCutoff > 0.0 && albedoSample.a < material.alphaCutoff) {
        discard;
    }
    vec3 worldNormal = computeWorldNormal();
    vec3 viewDirection = normalize(frame.cameraPosition.xyz - vertexWorldPosition);
    vec3 sampledAlbedo = albedoSample.rgb * material.baseColor;
    vec4 metallicRoughnessSample = texture(metallicRoughnessMap, vertexUv);
    float metallic = clamp(metallicRoughnessSample.b * material.metallic, 0.0, 1.0);
    float roughness = clamp(metallicRoughnessSample.g * material.roughness, 0.04, 1.0);
    float occlusion = texture(occlusionMap, vertexUv).r;

    vec3 direct = vec3(0.0);
    int lightCount = frame.lightCountAndShadowIndex.x;
    int shadowIndex = frame.lightCountAndShadowIndex.y;
    for (int i = 0; i < lightCount; i++) {
        vec3 toLight;
        vec3 lightRadiance;
        float attenuation;
        unpackLight(frame.lights[i], vertexWorldPosition, toLight, lightRadiance, attenuation);
        float shadow = (i == shadowIndex)
                ? sampleShadowFactor(vertexWorldPosition, worldNormal, toLight, vertexViewDepth)
                : 1.0;
        direct += cookTorranceBrdf(worldNormal, viewDirection, toLight,
                sampledAlbedo, metallic, roughness, lightRadiance * attenuation * shadow);
    }

    vec3 ambient = imageBasedAmbient(worldNormal, viewDirection, sampledAlbedo, metallic, roughness, occlusion);
    vec3 emissive = texture(emissiveMap, vertexUv).rgb * material.emissiveStrength;
    outColor = vec4(ambient + direct + emissive, 1.0);
}
