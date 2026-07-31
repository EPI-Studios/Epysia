#version 430 core

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUv;

layout(std140, binding = 0) uniform ImpostorViewUbo {
    mat4 viewProjectionModel;
    mat4 normalMatrix;
    vec4 baseColor;
    vec4 surfaceParameters;
} view;

out vec3 bakedNormal;
out vec2 texCoord;

void main() {
    bakedNormal = mat3(view.normalMatrix) * inNormal;
    texCoord = inUv;
    gl_Position = view.viewProjectionModel * vec4(inPosition, 1.0);
}
