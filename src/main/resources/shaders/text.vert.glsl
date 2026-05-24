#version 430 core
layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inUv;

layout(std140, binding = 0) uniform TextUbo {
    vec4 viewportSize;
    vec4 textColor;
} text;

out vec2 vertexUv;

void main() {
    vec2 ndc = (inPosition / text.viewportSize.xy) * 2.0 - 1.0;
    ndc.y = -ndc.y;
    gl_Position = vec4(ndc, 0.0, 1.0);
    vertexUv = inUv;
}
