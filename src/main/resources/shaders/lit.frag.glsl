#version 430 core
#ifdef MATERIAL_EARLY_DEPTH_TESTED
layout(early_fragment_tests) in;
#endif
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

#ifdef PROBE_LIT
layout(std430, binding = 5) readonly buffer ProbeShBuffer {
    float probeShCoefficients[];
};
#endif

in vec3 vertexWorldPosition;
in vec3 vertexWorldNormal;
in vec3 vertexWorldTangent;
in vec2 vertexUv;
in vec2 vertexLightmapUv;
in float vertexViewDepth;

#ifdef VERTEX_COLORED
in vec4 vertexColor;
#endif

layout(std140, binding = 2) uniform MaterialUbo {
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

layout(binding = 4) uniform sampler2D albedo;
layout(binding = 5) uniform sampler2D normalMap;
layout(binding = 6) uniform sampler2D metallicRoughnessMap;
layout(binding = 7) uniform sampler2D occlusionMap;
layout(binding = 8) uniform sampler2D emissiveMap;
layout(binding = 2) uniform sampler2D lightmap;
layout(binding = 9) uniform samplerCube irradianceMap;
layout(binding = 10) uniform samplerCube prefilteredMap;
layout(binding = 11) uniform sampler2D brdfLut;
flat in int surfaceInstanceIndex;

layout(binding = 14) uniform sampler2D opaqueSceneColor;
layout(binding = 15) uniform sampler2D opaqueSceneDepth;

#include "lib/uv_mapping.glsl"

vec2 screenUv() {
    vec4 clip = frame.cameraViewProjection * vec4(vertexWorldPosition, 1.0);
    return clip.xy / clip.w * 0.5 + 0.5;
}

vec3 sceneColorAt(vec2 uv) {
    return texture(opaqueSceneColor, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

float sceneViewDepthAt(vec2 uv) {
    float deviceDepth = texture(opaqueSceneDepth, clamp(uv, vec2(0.0), vec2(1.0))).r;
    float near = max(frame.clusterParams.x, 1.0e-4);
    float far = max(frame.clusterParams.y, near + 1.0e-3);
    float normalized = deviceDepth * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - normalized * (far - near));
}

float sceneDepthBehind(vec2 uv) {
    return max(sceneViewDepthAt(uv) - vertexViewDepth, 0.0);
}

vec3 sceneSurfaceNormalAt(vec2 uv);

vec3 sceneWorldPositionAt(vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(1.0));
    float deviceDepth = texture(opaqueSceneDepth, clamped).r;
    vec4 clip = vec4(clamped * 2.0 - 1.0, deviceDepth * 2.0 - 1.0, 1.0);
    vec4 world = frame.cameraInverseViewProjection * clip;
    return world.xyz / world.w;
}

const float MAX_REFLECTION_LOD = 4.0;

out vec4 outColor;

#ifdef PROBE_LIT
vec3 probeShIrradiance(int probeIndex, vec3 n) {
    int base = probeIndex * 27;
    vec3 irradiance = vec3(0.0);
    float factors[9] = float[](
        0.886227,
        1.023328 * n.y,
        1.023328 * n.z,
        1.023328 * n.x,
        0.858086 * n.x * n.y,
        0.858086 * n.y * n.z,
        0.743125 * n.z * n.z - 0.247708,
        0.858086 * n.x * n.z,
        0.429043 * (n.x * n.x - n.y * n.y));
    for (int coefficient = 0; coefficient < 9; coefficient++) {
        int offset = base + coefficient * 3;
        irradiance += factors[coefficient] * vec3(probeShCoefficients[offset],
                probeShCoefficients[offset + 1], probeShCoefficients[offset + 2]);
    }
    return max(irradiance * 0.3183099, vec3(0.0));
}

vec3 blendedProbeIrradiance(vec3 worldPosition, vec3 normal) {
    ivec3 resolution = frame.probeGridResolution.xyz;
    vec3 gridPosition = (worldPosition - frame.probeGridOrigin.xyz) / frame.probeGridSpacing.xyz;
    vec3 clamped = clamp(gridPosition, vec3(0.0), vec3(resolution) - 1.0);
    ivec3 cell = clamp(ivec3(floor(clamped)), ivec3(0), max(resolution - 2, ivec3(0)));
    vec3 fraction = clamp(clamped - vec3(cell), 0.0, 1.0);
    vec3 irradiance = vec3(0.0);
    for (int corner = 0; corner < 8; corner++) {
        ivec3 offset = ivec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
        ivec3 probe = min(cell + offset, resolution - 1);
        vec3 weights = mix(1.0 - fraction, fraction, vec3(offset));
        float weight = weights.x * weights.y * weights.z;
        int probeIndex = probe.x + probe.y * resolution.x + probe.z * resolution.x * resolution.y;
        irradiance += weight * probeShIrradiance(probeIndex, normal);
    }
    return irradiance;
}
#endif

vec3 imageBasedAmbient(vec3 normal, vec3 viewDirection, vec3 albedoColor,
                       float metallic, float roughness, float occlusion) {
    float normalDotView = max(dot(normal, viewDirection), 1.0e-4);
    vec3 baseReflectivity = mix(vec3(0.04), albedoColor, metallic);
    vec3 fresnel = fresnelSchlickRoughness(normalDotView, baseReflectivity, roughness);
    vec3 diffuseWeight = (1.0 - fresnel) * (1.0 - metallic);
#ifdef PROBE_LIT
    vec3 irradianceSample = blendedProbeIrradiance(vertexWorldPosition, normal);
#else
    vec3 irradianceSample = texture(irradianceMap, normal).rgb;
#endif
    vec3 reflected = reflect(-viewDirection, normal);
    vec3 prefilteredSample = textureLod(prefilteredMap, reflected, roughness * MAX_REFLECTION_LOD).rgb;
    vec2 environmentBrdf = texture(brdfLut, vec2(normalDotView, roughness)).rg;
    vec3 diffuse = diffuseWeight * irradianceSample * albedoColor;
    vec3 specular = prefilteredSample * (baseReflectivity * environmentBrdf.x + environmentBrdf.y);
    vec3 environment = (diffuse + specular) * occlusion * frame.ambientColor.rgb * frame.ambientColor.a;
#ifdef MATERIAL_HAS_LIGHTMAP
    vec2 lightmapUv = vertexLightmapUv * material.lightmapScaleOffset.xy + material.lightmapScaleOffset.zw;
    vec4 lightmapSample = texture(lightmap, lightmapUv);
    vec3 baked = material.lightmapRgbmRange > 0.0
            ? lightmapSample.rgb * lightmapSample.a * material.lightmapRgbmRange
            : lightmapSample.rgb;
    baked *= material.lightmapStrength;
    environment += baked * albedoColor * diffuseWeight * occlusion;
#endif
    return environment;
}

vec3 triplanarWorldNormal(vec3 geometricNormal) {
    vec3 position = triplanarPosition();
    vec3 weights = triplanarWeights();
    vec3 axisX = texture(normalMap, position.zy).rgb * 2.0 - 1.0;
    vec3 axisY = texture(normalMap, position.xz).rgb * 2.0 - 1.0;
    vec3 axisZ = texture(normalMap, position.xy).rgb * 2.0 - 1.0;
    axisX.xy *= material.normalScale;
    axisY.xy *= material.normalScale;
    axisZ.xy *= material.normalScale;
    axisX = vec3(axisX.xy + geometricNormal.zy, abs(axisX.z) * geometricNormal.x);
    axisY = vec3(axisY.xy + geometricNormal.xz, abs(axisY.z) * geometricNormal.y);
    axisZ = vec3(axisZ.xy + geometricNormal.xy, abs(axisZ.z) * geometricNormal.z);
    return normalize(axisX.zyx * weights.x + axisY.xzy * weights.y + axisZ.xyz * weights.z);
}

vec3 computeWorldNormal() {
#ifdef MATERIAL_HAS_NORMALMAP
    vec3 normal = normalize(vertexWorldNormal) * (gl_FrontFacing ? 1.0 : -1.0);
    if (material.uvMapping != UV_MAPPING_MESH) {
        return triplanarWorldNormal(normal);
    }
    vec3 sampledNormal = texture(normalMap, mappedUv(vertexUv)).rgb * 2.0 - 1.0;
    sampledNormal *= vec3(material.normalScale, material.normalScale, 1.0);
    vec3 tangent = normalize(vertexWorldTangent - normal * dot(normal, vertexWorldTangent));
    vec3 bitangent = cross(normal, tangent);
    mat3 tangentToWorld = mat3(tangent, bitangent, normal);
    return normalize(tangentToWorld * sampledNormal);
#else
    return normalize(vertexWorldNormal) * (gl_FrontFacing ? 1.0 : -1.0);
#endif
}

vec3 sceneSurfaceNormalAt(vec2 uv) {
    vec3 position = sceneWorldPositionAt(uv);
    return normalize(cross(dFdx(position), dFdy(position)) + vec3(0.0, 1.0e-4, 0.0));
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
    float sourceRadius;
    float lightDistance;
    unpackLight(light, vertexWorldPosition, toLight, lightRadiance, attenuation,
            sourceRadius, lightDistance);
    if (attenuation <= 0.0) {
        return vec3(0.0);
    }
    int lightType = int(light.positionAndType.w);
    float shadow = 1.0;
#ifndef MATERIAL_NO_SHADOWS
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
#endif
    vec3 radiance = lightRadiance * attenuation * shadow;
#ifdef SURFACE_LIGHT_ENABLED
    vec3 shaded = vec3(0.0);
    surfaceLight(shaded, worldNormal, viewDirection, toLight, albedo, metallic, roughness,
            radiance, lightType);
    return shaded;
#else
    return cookTorranceSphereBrdf(worldNormal, viewDirection, toLight, lightDistance, sourceRadius,
            albedo, metallic, roughness, radiance);
#endif
}

void main() {
    // SURFACE_PREPARE_CALL
#ifdef MATERIAL_HAS_ALBEDO
    vec4 albedoSample = materialTexture(albedo, vertexUv);
#else
    vec4 albedoSample = vec4(1.0);
#endif
    vec4 albedoColor = vec4(albedoSample.rgb * material.baseColor, albedoSample.a);
#ifdef VERTEX_COLORED
    albedoColor *= vertexColor;
#endif
#ifdef MATERIAL_HAS_METALLICROUGHNESSMAP
    vec4 metallicRoughnessSample = materialTexture(metallicRoughnessMap, vertexUv);
#else
    vec4 metallicRoughnessSample = vec4(1.0);
#endif
    float metallic = clamp(metallicRoughnessSample.b * material.metallic, 0.0, 1.0);
    float roughness = clamp(metallicRoughnessSample.g * material.roughness, 0.04, 1.0);
#ifdef MATERIAL_HAS_EMISSIVEMAP
    vec3 emissive = materialTexture(emissiveMap, vertexUv).rgb * material.emissiveStrength;
#else
    vec3 emissive = vec3(material.emissiveStrength);
#endif
    // SURFACE_COLOR_CALL
#ifdef MATERIAL_ALPHA_MASKED
    if (material.alphaCutoff > 0.0 && albedoColor.a < material.alphaCutoff) {
        discard;
    }
#endif
#ifdef SURFACE_UNSHADED
    outColor = vec4(albedoColor.rgb + emissive, albedoColor.a);
    return;
#endif
    vec3 worldNormal = computeWorldNormal();
    vec3 viewDirection = normalize(frame.cameraPosition.xyz - vertexWorldPosition);
    // SURFACE_NORMAL_CALL
    // SURFACE_SHADE_CALL
    vec3 sampledAlbedo = albedoColor.rgb;
    roughness = clamp(filteredRoughness(worldNormal, roughness), 0.04, 1.0);
    metallic = clamp(metallic, 0.0, 1.0);
#ifdef MATERIAL_HAS_OCCLUSIONMAP
    float occlusion = mix(1.0, materialTexture(occlusionMap, vertexUv).r, material.occlusionStrength);
#else
    float occlusion = 1.0;
#endif

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
    outColor = vec4(ambient + direct + emissive, albedoColor.a);
}
