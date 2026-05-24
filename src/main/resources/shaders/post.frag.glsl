#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D sceneColor;
layout(binding = 2) uniform sampler2D sceneDepth;

layout(std140, binding = 1) uniform PostUbo {
    vec4 vignetteParams;
    vec4 gradeParams;
    vec4 fogColor;
    vec4 fogDistance;
    vec4 cameraDepth;
    vec4 cameraPosition;
    mat4 inverseViewProjection;
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

float computeFogFactor(vec2 uv) {
    float rawDepth = texture(sceneDepth, uv).r;
    if (rawDepth >= 1.0) {
        return 1.0;
    }
    float viewDistance = linearizeDepth(rawDepth, post.cameraDepth.x, post.cameraDepth.y);
    float distanceBeyondStart = max(viewDistance - post.fogDistance.x, 0.0);
    float distanceTerm = 1.0 - exp(-pow(distanceBeyondStart * post.fogDistance.y, 2.0));

    vec3 worldPosition = reconstructWorldPosition(uv, rawDepth);
    float heightAboveOrigin = worldPosition.y - post.fogDistance.z;
    float heightAttenuation = exp(-max(heightAboveOrigin, 0.0) * post.fogDistance.w);
    float heightTerm = heightAttenuation * post.cameraDepth.z;

    float combined = 1.0 - (1.0 - distanceTerm) * (1.0 - clamp(heightTerm, 0.0, 1.0));
    return clamp(combined, 0.0, 1.0);
}

void main() {
    vec3 color = texture(sceneColor, vertexUv).rgb;

    float fogStrength = post.fogColor.w;
    if (fogStrength > 0.0) {
        float fogFactor = computeFogFactor(vertexUv) * fogStrength;
        color = mix(color, post.fogColor.rgb, fogFactor);
    }

    vec2 centered = vertexUv * 2.0 - 1.0;
    float radial = dot(centered, centered);
    float vignette = 1.0 - radial * post.vignetteParams.x;
    vignette = clamp(vignette, 0.0, 1.0);
    color *= vignette;

    color = pow(color, vec3(post.gradeParams.x));
    color *= post.gradeParams.y;

    color = pow(color, vec3(1.0 / 2.2));

    outColor = vec4(color, 1.0);
}
