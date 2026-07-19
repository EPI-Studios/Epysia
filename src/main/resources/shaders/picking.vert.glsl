#version 430 core
#include "lib/frame_uniforms.glsl"

layout(location = 0) in vec3 inPosition;

void main() {
    gl_Position = frame.cameraViewProjection * object.model * vec4(inPosition, 1.0);
}
