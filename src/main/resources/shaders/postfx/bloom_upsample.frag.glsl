#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D sourceTexture;

out vec4 outColor;

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(sourceTexture, 0));
    vec3 sum = texture(sourceTexture, vertexUv).rgb * 4.0;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(-1.0, 0.0)).rgb * 2.0;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(1.0, 0.0)).rgb * 2.0;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(0.0, -1.0)).rgb * 2.0;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(0.0, 1.0)).rgb * 2.0;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(-1.0, -1.0)).rgb;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(1.0, -1.0)).rgb;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(-1.0, 1.0)).rgb;
    sum += texture(sourceTexture, vertexUv + texelSize * vec2(1.0, 1.0)).rgb;
    outColor = vec4(sum / 16.0, 1.0);
}
