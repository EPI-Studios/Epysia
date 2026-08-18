#version 430 core

layout(location = 0) in vec3 inPosition;

layout(std140, binding = 0) uniform DecalUbo {
    mat4 viewProjection;
    mat4 inverseViewProjection;
    mat4 model;
    mat4 inverseModel;
    vec4 tintAndOpacity;
    vec4 uvScaleAndOffset;
    vec4 fadeAndScreen;
    ivec4 masks;
} decal;

void main() {
    gl_Position = decal.viewProjection * decal.model * vec4(inPosition, 1.0);
}
