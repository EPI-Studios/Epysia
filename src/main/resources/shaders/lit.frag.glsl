#version 430 core
#include "lib/frame_uniforms.glsl"
#include "lib/lighting.glsl"

in vec3 vertexWorldPosition;
in vec3 vertexWorldNormal;
in vec3 vertexWorldTangent;
in vec2 vertexUv;
in vec4 vertexLightSpacePosition;

layout(std140, binding = 2) uniform MaterialUbo {
    vec3 baseColor;
    float shininess;
    float specularStrength;
} material;

layout(binding = 4) uniform sampler2D albedo;
layout(binding = 5) uniform sampler2D normalMap;

out vec4 outColor;

vec3 computeWorldNormal() {
    vec3 sampledNormal = texture(normalMap, vertexUv).rgb * 2.0 - 1.0;
    vec3 normal = normalize(vertexWorldNormal);
    vec3 tangent = normalize(vertexWorldTangent - normal * dot(normal, vertexWorldTangent));
    vec3 bitangent = cross(normal, tangent);
    mat3 tangentToWorld = mat3(tangent, bitangent, normal);
    return normalize(tangentToWorld * sampledNormal);
}

void main() {
    vec3 worldNormal = computeWorldNormal();
    vec3 viewDirection = normalize(frame.cameraPosition.xyz - vertexWorldPosition);
    vec3 sampledAlbedo = texture(albedo, vertexUv).rgb * material.baseColor;

    vec3 totalDiffuse = vec3(0.0);
    vec3 totalSpecular = vec3(0.0);
    int lightCount = frame.lightCountAndShadowIndex.x;
    int shadowIndex = frame.lightCountAndShadowIndex.y;

    for (int i = 0; i < lightCount; i++) {
        vec3 toLight;
        vec3 lightRadiance;
        float attenuation;
        unpackLight(frame.lights[i], vertexWorldPosition, toLight, lightRadiance, attenuation);
        float diffuseTerm = max(dot(worldNormal, toLight), 0.0);
        vec3 halfway = normalize(toLight + viewDirection);
        float specularTerm = pow(max(dot(worldNormal, halfway), 0.0), max(material.shininess, 1.0));
        float shadow = (i == shadowIndex) ? sampleShadow(vertexLightSpacePosition) : 1.0;
        totalDiffuse += attenuation * shadow * diffuseTerm * lightRadiance;
        totalSpecular += attenuation * shadow * specularTerm * material.specularStrength * lightRadiance;
    }

    vec3 ambient = frame.ambientColor.rgb * sampledAlbedo;
    vec3 lit = ambient + sampledAlbedo * totalDiffuse + totalSpecular;
    outColor = vec4(lit, 1.0);
}
