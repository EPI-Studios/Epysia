#version 430 core
#include "lib/frame_uniforms.glsl"
#include "vfx/particle_common.glsl"

layout(location = 0) in vec3 inPosition;

out vec2 particleCorner;
out vec4 particleColor;
out float particleViewDepth;

void main() {
    uint slot = aliveIndices[gl_InstanceID];
    Particle particle = particles[slot];
    vec2 corners[4] = vec2[4](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
    vec2 corner = corners[gl_VertexID & 3];
    vec3 toCamera = normalize(frame.cameraPosition.xyz - particle.positionAge.xyz);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), toCamera));
    vec3 up = cross(toCamera, right);
    vec2 halfSize = particle.sizeRotation.xy * effect.renderParams.x;
    float angle = radians(particle.sizeRotation.z);
    vec2 scaled = corner * halfSize;
    vec2 turned = vec2(scaled.x * cos(angle) - scaled.y * sin(angle),
            scaled.x * sin(angle) + scaled.y * cos(angle));
    vec3 world = particle.positionAge.xyz + right * turned.x + up * turned.y;
    particleCorner = corner;
    particleColor = particle.color;
    gl_Position = frame.cameraViewProjection * vec4(world, 1.0);
    particleViewDepth = gl_Position.w;
}
