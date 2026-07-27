#version 430 core

const int MAXIMUM_OUTLINE_RADIUS = 6;

layout(std140, binding = 0) uniform OutlineUbo {
    vec4 outlineColor;
    vec4 outlineParams;
} outline;

layout(binding = 1) uniform sampler2D maskTexture;

in vec2 vertexUv;

out vec4 fragColor;

float maskAt(vec2 uv) {
    return texture(maskTexture, uv).r;
}

void main() {
    if (maskAt(vertexUv) > 0.5) {
        fragColor = vec4(outline.outlineColor.rgb, outline.outlineColor.a * outline.outlineParams.w);
        return;
    }
    float radius = outline.outlineParams.z;
    float squaredRadius = radius * radius;
    vec2 texel = outline.outlineParams.xy;
    float neighbour = 0.0;
    for (int y = -MAXIMUM_OUTLINE_RADIUS; y <= MAXIMUM_OUTLINE_RADIUS; y++) {
        for (int x = -MAXIMUM_OUTLINE_RADIUS; x <= MAXIMUM_OUTLINE_RADIUS; x++) {
            float squaredDistance = float(x * x + y * y);
            if (squaredDistance > squaredRadius) {
                continue;
            }
            neighbour = max(neighbour, maskAt(vertexUv + vec2(float(x), float(y)) * texel));
        }
    }
    if (neighbour < 0.5) {
        discard;
    }
    fragColor = vec4(outline.outlineColor.rgb, outline.outlineColor.a);
}
