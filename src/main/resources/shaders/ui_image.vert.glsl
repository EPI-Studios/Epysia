#version 430 core
layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inUv;
layout(location = 2) in vec4 inColor;

layout(std140, binding = 0) uniform UiUbo {
    vec4 viewportSize;
    vec4 time;
} ui;

out vec2 vertexUv;
out vec4 vertexColor;

void main() {
    vec2 ndc = (inPosition / ui.viewportSize.xy) * 2.0 - 1.0;
    ndc.y = -ndc.y;
    gl_Position = vec4(ndc, 0.0, 1.0);
    vertexUv = inUv;
    vertexColor = inColor;
}
