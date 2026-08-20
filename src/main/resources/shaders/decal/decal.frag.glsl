#version 430 core

layout(std140, binding = 0) uniform DecalUbo {
    mat4 viewProjection;
    mat4 inverseViewProjection;
    mat4 model;
    mat4 inverseModel;
    vec4 tintAndOpacity;
    vec4 uvScaleAndOffset;
    vec4 fadeAndScreen;
    ivec4 masks;
} decal;

layout(binding = 1) uniform sampler2D sceneDepth;
layout(binding = 2) uniform sampler2D sceneNormal;
layout(binding = 3) uniform sampler2D decalTexture;

out vec4 outColor;

vec3 worldFromDepth(vec2 uv, float deviceDepth) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, deviceDepth * 2.0 - 1.0, 1.0);
    vec4 worldPosition = decal.inverseViewProjection * clipPosition;
    return worldPosition.xyz / worldPosition.w;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / vec2(textureSize(sceneDepth, 0));
    float deviceDepth = texture(sceneDepth, screenUv).r;
    if (deviceDepth >= 1.0) {
        discard;
    }
    vec3 worldPosition = worldFromDepth(screenUv, deviceDepth);
    vec3 localPosition = (decal.inverseModel * vec4(worldPosition, 1.0)).xyz;
    if (any(greaterThan(abs(localPosition), vec3(0.5)))) {
        discard;
    }
    vec4 normalLayer = texture(sceneNormal, screenUv);
    int surfaceLayer = int(normalLayer.a + 0.5);
    if ((decal.masks.x & (1 << surfaceLayer)) == 0) {
        discard;
    }
    vec3 surfaceNormal = normalize(normalLayer.xyz);
    vec3 facingAxis = normalize(mat3(decal.model)[2]);
    float fade = smoothstep(decal.fadeAndScreen.x, 1.0, dot(surfaceNormal, facingAxis));
    if (fade <= 0.0) {
        discard;
    }
    vec2 decalUv = vec2(localPosition.x + 0.5, 0.5 - localPosition.y);
    decalUv = decalUv * decal.uvScaleAndOffset.xy + decal.uvScaleAndOffset.zw;
    vec4 sampled = texture(decalTexture, decalUv);
    float coverage = sampled.a * decal.tintAndOpacity.w * fade;
    vec3 color = sampled.rgb * decal.tintAndOpacity.rgb;
    if (decal.masks.y == 1) {
        outColor = vec4(mix(vec3(1.0), color, coverage), 1.0);
        return;
    }
    if (decal.masks.y == 2) {
        outColor = vec4(color * coverage, coverage);
        return;
    }
    outColor = vec4(color, coverage);
}
