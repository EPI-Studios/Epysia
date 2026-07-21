#version 430 core
#include "lib/frame_uniforms.glsl"
#include "lib/lighting.glsl"
#include "lib/pbr.glsl"
#include "lib/shadows.glsl"

layout(std430, binding = 0) readonly buffer LightBuffer {
    Light lights[];
} lightBuffer;

layout(std430, binding = 1) readonly buffer ClusterCounts {
    uint counts[];
} clusterCounts;

layout(std430, binding = 2) readonly buffer ClusterIndices {
    uint indices[];
} clusterIndices;

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

int computeClusterIndex() {
    vec4 clip = frame.cameraViewProjection * vec4(vertexWorldPosition, 1.0);
    vec2 ndc = clip.xy / clip.w;
    int gx = frame.clusterGrid.x;
    int gy = frame.clusterGrid.y;
    int gz = frame.clusterGrid.z;
    int tileX = clamp(int((ndc.x * 0.5 + 0.5) * float(gx)), 0, gx - 1);
    int tileY = clamp(int((ndc.y * 0.5 + 0.5) * float(gy)), 0, gy - 1);
    int slice = clamp(int(log(vertexViewDepth) * frame.clusterSliceParams.x - frame.clusterSliceParams.y), 0, gz - 1);
    return tileX + tileY * gx + slice * gx * gy;
}

// SURFACE_FUNCTIONS

vec3 shadeLight(int lightIndex, int shadowIndex, vec3 worldNormal, vec3 viewDirection,
                vec3 albedo, float metallic, float roughness) {
    Light light = lightBuffer.lights[lightIndex];
    vec3 toLight;
    vec3 lightRadiance;
    float attenuation;
    unpackLight(light, vertexWorldPosition, toLight, lightRadiance, attenuation);
    int lightType = int(light.positionAndType.w);
    float shadow = 1.0;
    if (lightIndex == shadowIndex) {
        shadow = sampleShadowFactor(vertexWorldPosition, worldNormal, toLight, vertexViewDepth);
    } else if (lightType == LIGHT_TYPE_SPOT) {
        int spotLayer = int(light.spotCones.z);
        if (spotLayer >= 0) {
            shadow = sampleSpotShadow(vertexWorldPosition, worldNormal, toLight, spotLayer);
        }
    } else if (lightType == LIGHT_TYPE_POINT) {
        int pointIndex = int(light.directionAndRange.x);
        if (pointIndex >= 0) {
            shadow = samplePointShadow(vertexWorldPosition, light.positionAndType.xyz, worldNormal, pointIndex);
        }
    }
    vec3 radiance = lightRadiance * attenuation * shadow;
#ifdef SURFACE_LIGHT_ENABLED
    vec3 shaded = vec3(0.0);
    surfaceLight(shaded, worldNormal, viewDirection, toLight, albedo, metallic, roughness,
            radiance, lightType);
    return shaded;
#else
    return cookTorranceBrdf(worldNormal, viewDirection, toLight,
            albedo, metallic, roughness, radiance);
#endif
}

void main() {
    vec4 albedoSample = texture(albedo, vertexUv);
    vec4 albedoColor = vec4(albedoSample.rgb * material.baseColor, albedoSample.a);
    vec4 metallicRoughnessSample = texture(metallicRoughnessMap, vertexUv);
    float metallic = clamp(metallicRoughnessSample.b * material.metallic, 0.0, 1.0);
    float roughness = clamp(metallicRoughnessSample.g * material.roughness, 0.04, 1.0);
    vec3 emissive = texture(emissiveMap, vertexUv).rgb * material.emissiveStrength;
    // SURFACE_COLOR_CALL
    if (material.alphaCutoff > 0.0 && albedoColor.a < material.alphaCutoff) {
        discard;
    }
#ifdef SURFACE_UNSHADED
    outColor = vec4(albedoColor.rgb + emissive, albedoColor.a);
    return;
#endif
    vec3 worldNormal = computeWorldNormal();
    vec3 viewDirection = normalize(frame.cameraPosition.xyz - vertexWorldPosition);
    vec3 sampledAlbedo = albedoColor.rgb;
    roughness = clamp(roughness, 0.04, 1.0);
    metallic = clamp(metallic, 0.0, 1.0);
    float occlusion = texture(occlusionMap, vertexUv).r;

    vec3 direct = vec3(0.0);
    int lightCount = frame.lightCountAndShadowIndex.x;
    int shadowIndex = frame.lightCountAndShadowIndex.y;
    if (frame.clusterGrid.w != 0) {
        int directionalCount = frame.lightCountAndShadowIndex.w;
        for (int i = 0; i < directionalCount; i++) {
            direct += shadeLight(i, shadowIndex, worldNormal, viewDirection,
                    sampledAlbedo, metallic, roughness);
        }
        int cluster = computeClusterIndex();
        int maxPerCluster = int(frame.clusterSliceParams.z);
        uint clusterLightCount = clusterCounts.counts[cluster];
        for (uint i = 0u; i < clusterLightCount; i++) {
            int lightIndex = int(clusterIndices.indices[cluster * maxPerCluster + int(i)]);
            direct += shadeLight(lightIndex, shadowIndex, worldNormal, viewDirection,
                    sampledAlbedo, metallic, roughness);
        }
    } else {
        for (int i = 0; i < lightCount; i++) {
            direct += shadeLight(i, shadowIndex, worldNormal, viewDirection,
                    sampledAlbedo, metallic, roughness);
        }
    }

    vec3 ambient = imageBasedAmbient(worldNormal, viewDirection, sampledAlbedo, metallic, roughness, occlusion);
    outColor = vec4(ambient + direct + emissive, 1.0);
}
