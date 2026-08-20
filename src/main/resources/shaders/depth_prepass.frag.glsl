#version 430 core

in vec3 vertexWorldNormal;
flat in float vertexRenderLayer;

layout(location = 0) out vec4 outNormalLayer;

void main() {
    outNormalLayer = vec4(normalize(vertexWorldNormal), vertexRenderLayer);
}
