#version 430 core
#include "lib/tonemap.glsl"
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D sceneColor;
layout(binding = 2) uniform sampler2D sceneDepth;
layout(binding = 3) uniform sampler2D bloomTexture;
layout(binding = 4) uniform sampler2D ambientOcclusion;

layout(std140, binding = 1) uniform PostUbo {
    vec4 vignetteParams;
    vec4 gradeParams;
    vec4 fogColor;
    vec4 fogDistance;
    vec4 cameraDepth;
    vec4 cameraPosition;
    mat4 inverseViewProjection;
    vec4 effectParams;
} post;

out vec4 outColor;

float linearizeDepth(float ndcDepth, float nearPlane, float farPlane) {
    float clipZ = ndcDepth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - clipZ * (farPlane - nearPlane));
}

vec3 reconstructWorldPosition(vec2 uv, float ndcDepth) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, ndcDepth * 2.0 - 1.0, 1.0);
    vec4 worldPosition = post.inverseViewProjection * clipPosition;
    return worldPosition.xyz / worldPosition.w;
}

const float OCCLUSION_DEPTH_TOLERANCE_SCALE = 0.05;
const float OCCLUSION_DEPTH_TOLERANCE_MINIMUM = 0.05;

float sampleAmbientOcclusion(vec2 uv) {
    float rawDepth = texture(sceneDepth, uv).r;
    if (rawDepth >= 1.0) {
        return 1.0;
    }
    float centerDepth = linearizeDepth(rawDepth, post.cameraDepth.x, post.cameraDepth.y);
    vec2 occlusionSize = vec2(textureSize(ambientOcclusion, 0));
    vec2 occlusionTexel = 1.0 / occlusionSize;
    vec2 baseUv = (floor(uv * occlusionSize - 0.5) + 0.5) * occlusionTexel;
    float bestDifference = 1.0e9;
    float bestOcclusion = 1.0;
    float maximumDifference = 0.0;
    for (int y = 0; y <= 1; y++) {
        for (int x = 0; x <= 1; x++) {
            vec2 neighborUv = baseUv + vec2(float(x), float(y)) * occlusionTexel;
            float neighborDepth = linearizeDepth(texture(sceneDepth, neighborUv).r, post.cameraDepth.x, post.cameraDepth.y);
            float difference = abs(neighborDepth - centerDepth);
            maximumDifference = max(maximumDifference, difference);
            if (difference < bestDifference) {
                bestDifference = difference;
                bestOcclusion = texture(ambientOcclusion, neighborUv).r;
            }
        }
    }
    float tolerance = centerDepth * OCCLUSION_DEPTH_TOLERANCE_SCALE + OCCLUSION_DEPTH_TOLERANCE_MINIMUM;
    return maximumDifference < tolerance ? texture(ambientOcclusion, uv).r : bestOcclusion;
}

float fogDistanceStart() {
    return post.fogDistance.x;
}

float fogDistanceDensity() {
    return post.fogDistance.y;
}

float fogHeightOrigin() {
    return post.fogDistance.z;
}

float fogHeightFalloff() {
    return post.fogDistance.w;
}

float fogHeightDensity() {
    return post.cameraDepth.z;
}

vec3 fogTint() {
    return post.fogColor.rgb;
}

vec3 cameraPosition() {
    return post.cameraPosition.xyz;
}

float builtinFogFactor(vec3 worldPosition, float viewDistance) {
    float distanceBeyondStart = max(viewDistance - fogDistanceStart(), 0.0);
    float distanceTerm = 1.0 - exp(-pow(distanceBeyondStart * fogDistanceDensity(), 2.0));

    float heightAboveOrigin = worldPosition.y - fogHeightOrigin();
    float heightAttenuation = exp(-max(heightAboveOrigin, 0.0) * fogHeightFalloff());
    float heightTerm = heightAttenuation * fogHeightDensity();

    float combined = 1.0 - (1.0 - distanceTerm) * (1.0 - clamp(heightTerm, 0.0, 1.0));
    return clamp(combined, 0.0, 1.0);
}

// FOG_FUNCTIONS

vec3 applyFog(vec3 color, vec2 uv, float strength) {
    float rawDepth = texture(sceneDepth, uv).r;
    if (rawDepth >= 1.0) {
        return color;
    }
    float viewDistance = linearizeDepth(rawDepth, post.cameraDepth.x, post.cameraDepth.y);
    vec3 worldPosition = reconstructWorldPosition(uv, rawDepth);
#ifdef FOG_SHADER_ENABLED
    vec4 fog = fogShade(worldPosition, viewDistance, uv, post.effectParams.w);
    return mix(color, fog.rgb, clamp(fog.a, 0.0, 1.0) * strength);
#else
    return mix(color, fogTint(), builtinFogFactor(worldPosition, viewDistance) * strength);
#endif
}

void main() {
    vec3 color = texture(sceneColor, vertexUv).rgb;

    float occlusionStrength = post.effectParams.x;
    if (occlusionStrength > 0.0) {
        float occlusion = sampleAmbientOcclusion(vertexUv);
        color *= mix(1.0, occlusion, occlusionStrength);
    }

    float bloomIntensity = post.effectParams.y;
    if (bloomIntensity > 0.0) {
        color += texture(bloomTexture, vertexUv).rgb * bloomIntensity;
    }

    float fogStrength = post.fogColor.w;
    if (fogStrength > 0.0) {
        color = applyFog(color, vertexUv, fogStrength);
    }

    color *= max(post.gradeParams.y, 0.0);
    color = acesFilmTonemap(color);
    color = pow(color, vec3(post.gradeParams.x));

    vec2 centered = vertexUv * 2.0 - 1.0;
    float radial = dot(centered, centered);
    float vignette = clamp(1.0 - radial * post.vignetteParams.x, 0.0, 1.0);
    color *= vignette;

    color = linearToSrgb(color);
    outColor = vec4(color, dot(color, vec3(0.299, 0.587, 0.114)));
}
