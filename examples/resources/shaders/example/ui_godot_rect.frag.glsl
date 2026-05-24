#version 430 core
in vec2 vertexUv;
in vec4 vertexColor;

layout(std140, binding = 0) uniform UiUbo {
    vec4 viewportSize;
    vec4 time;
} ui;

layout(binding = 1) uniform sampler2D noiseTexture;

out vec4 outColor;

const float RECT_HALF_WIDTH = 0.38;
const float RECT_HALF_HEIGHT = 0.30;
const float RECT_RADIUS = 0.18;
const float NOISE_SPEED = 0.10;
const float EDGE_THICKNESS = 0.05;
const float DISTORTION = 0.10;
const vec4 EDGE_COLOR = vec4(1.00, 0.55, 0.25, 1.0);
const vec4 INNER_COLOR = vec4(0.10, 0.05, 0.20, 1.0);

float sdRoundRect(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    vec2 uv = vertexUv;
    float noiseSample = texture(noiseTexture, fract(ui.time.x * NOISE_SPEED + uv)).r;
    float distance = sdRoundRect(uv - 0.5, vec2(RECT_HALF_WIDTH, RECT_HALF_HEIGHT), RECT_RADIUS);
    float inside = step(distance - noiseSample * DISTORTION, 0.0);
    float insideExpanded = step(distance - noiseSample * DISTORTION - EDGE_THICKNESS, 0.0);
    float ring = insideExpanded - inside;
    vec4 edge = ring * EDGE_COLOR;
    vec4 inner = inside * INNER_COLOR;
    outColor = mix(edge, inner, inner.a) * vertexColor;
}
