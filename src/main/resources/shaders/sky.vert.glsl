#version 430 core
layout(location = 0) in vec2 inPosition;

out vec2 vertexUv;

void main() {
    gl_Position = vec4(inPosition, 1.0, 1.0);
    vertexUv = (inPosition + 1.0) * 0.5;
}
