#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D sceneDepth;
layout(binding = 2) uniform sampler2D sceneNormal;

layout(std140, binding = 1) uniform SsaoUbo {
    mat4 viewProjection;
    mat4 inverseViewProjection;
    vec4 params;
    vec4 cameraDepth;
} ssao;

out vec4 outColor;

const int SAMPLE_COUNT = 24;
const float GOLDEN_ANGLE = 2.39996322973;
const float FULL_TURN = 6.28318530718;
const float MINIMUM_RADIUS_SCALE = 0.1;

float linearizeDepth(float ndcDepth) {
    float nearPlane = ssao.cameraDepth.x;
    float farPlane = ssao.cameraDepth.y;
    float clipZ = ndcDepth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - clipZ * (farPlane - nearPlane));
}

vec3 reconstructWorld(vec2 uv, float ndcDepth) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, ndcDepth * 2.0 - 1.0, 1.0);
    vec4 worldPosition = ssao.inverseViewProjection * clipPosition;
    return worldPosition.xyz / worldPosition.w;
}

float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

mat3 buildBasis(vec3 normal) {
    vec3 upAxis = abs(normal.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(upAxis, normal));
    vec3 bitangent = cross(normal, tangent);
    return mat3(tangent, bitangent, normal);
}

vec3 hemisphereSample(int index, float rotation) {
    float placement = (float(index) + 0.5) / float(SAMPLE_COUNT);
    float radial = sqrt(placement);
    float angle = float(index) * GOLDEN_ANGLE + rotation;
    return vec3(radial * cos(angle), radial * sin(angle), sqrt(1.0 - placement));
}

float sampleOcclusion(vec3 worldPosition, float referenceDepth, vec3 offset) {
    vec4 clipPosition = ssao.viewProjection * vec4(worldPosition + offset, 1.0);
    vec3 projected = clipPosition.xyz / max(clipPosition.w, 1.0e-5) * 0.5 + 0.5;
    if (any(lessThan(projected.xy, vec2(0.0))) || any(greaterThan(projected.xy, vec2(1.0)))) {
        return 0.0;
    }
    float sceneSampleDepth = linearizeDepth(texture(sceneDepth, projected.xy).r);
    float sampleDepth = linearizeDepth(projected.z);
    float rangeCheck = smoothstep(0.0, 1.0, ssao.params.x / max(abs(referenceDepth - sceneSampleDepth), 1.0e-4));
    return (sceneSampleDepth < sampleDepth - ssao.params.z) ? rangeCheck : 0.0;
}

void main() {
    float rawDepth = texture(sceneDepth, vertexUv).r;
    if (rawDepth >= 1.0) {
        outColor = vec4(1.0);
        return;
    }
    vec3 worldPosition = reconstructWorld(vertexUv, rawDepth);
    vec3 normal = ssao.cameraDepth.z > 0.5
            ? normalize(texture(sceneNormal, vertexUv).xyz)
            : normalize(cross(dFdx(worldPosition), dFdy(worldPosition)));
    float referenceDepth = linearizeDepth(rawDepth);
    float rotation = interleavedGradientNoise(gl_FragCoord.xy) * FULL_TURN;
    mat3 basis = buildBasis(normal);

    float occlusion = 0.0;
    for (int i = 0; i < SAMPLE_COUNT; i++) {
        float progress = float(i) / float(SAMPLE_COUNT);
        float radiusScale = mix(MINIMUM_RADIUS_SCALE, 1.0, progress * progress);
        vec3 offset = basis * hemisphereSample(i, rotation) * (ssao.params.x * radiusScale);
        occlusion += sampleOcclusion(worldPosition, referenceDepth, offset);
    }
    float exposure = clamp(1.0 - (occlusion / float(SAMPLE_COUNT)) * ssao.params.y, 0.0, 1.0);
    outColor = vec4(vec3(pow(exposure, ssao.params.w)), 1.0);
}
