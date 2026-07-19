#version 430 core
in vec2 vertexUv;

#include "postfx/ssao_blur_common.glsl"

out vec4 outColor;

void main() {
    outColor = vec4(vec3(blurAmbientOcclusion(vertexUv, vec2(0.0, 1.0))), 1.0);
}
