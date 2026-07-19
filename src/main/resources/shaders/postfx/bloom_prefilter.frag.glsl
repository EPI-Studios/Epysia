#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D sourceTexture;

layout(std140, binding = 1) uniform BloomUbo {
    vec4 thresholdParams;
} bloom;

out vec4 outColor;

vec3 sampleBox(vec2 uv) {
    vec2 texelSize = 1.0 / vec2(textureSize(sourceTexture, 0));
    vec3 sum = texture(sourceTexture, uv + texelSize * vec2(-1.0, -1.0)).rgb;
    sum += texture(sourceTexture, uv + texelSize * vec2(1.0, -1.0)).rgb;
    sum += texture(sourceTexture, uv + texelSize * vec2(-1.0, 1.0)).rgb;
    sum += texture(sourceTexture, uv + texelSize * vec2(1.0, 1.0)).rgb;
    return sum * 0.25;
}

void main() {
    vec3 color = sampleBox(vertexUv);
    float threshold = bloom.thresholdParams.x;
    float knee = bloom.thresholdParams.y;
    float brightness = max(color.r, max(color.g, color.b));
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = (soft * soft) / max(4.0 * knee, 1.0e-4);
    float contribution = max(soft, brightness - threshold) / max(brightness, 1.0e-4);
    outColor = vec4(color * contribution, 1.0);
}
