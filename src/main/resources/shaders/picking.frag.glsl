#version 430 core

layout(std140, binding = 2) uniform PickingUbo {
    vec4 idColor;
} picking;

out vec4 fragColor;

void main() {
    fragColor = vec4(picking.idColor.rgb, 1.0);
}
