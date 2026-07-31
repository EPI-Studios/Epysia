#version 430 core

layout(std140, binding = 0) uniform ImpostorViewUbo {
    mat4 viewProjectionModel;
    mat4 normalMatrix;
    vec4 baseColor;
    vec4 surfaceParameters;
} view;

layout(binding = 1) uniform sampler2D albedoMap;

in vec3 bakedNormal;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(albedoMap, texCoord);
    float coverage = view.surfaceParameters.y > 0.5 ? 1.0 : sampled.a * view.baseColor.a;
    if (coverage < view.surfaceParameters.x || coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(sampled.rgb * view.baseColor.rgb * coverage, coverage);
}
