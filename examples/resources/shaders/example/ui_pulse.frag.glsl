#version 430 core
in vec4 vertexColor;
in vec2 localPosition;

layout(std140, binding = 0) uniform UiUbo {
    vec4 viewportSize;
    vec4 time;
} ui;

out vec4 outColor;

void main() {
    float pulse = 0.5 + 0.5 * sin(ui.time.x * 4.0 + localPosition.x * 0.05);
    vec3 tinted = vertexColor.rgb * (0.3 + 0.7 * pulse);
    outColor = vec4(tinted, vertexColor.a);
}
