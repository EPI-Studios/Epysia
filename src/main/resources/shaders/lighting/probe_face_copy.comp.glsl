#version 430 core

#ifndef PROBE_FACE_SIZE
#define PROBE_FACE_SIZE 64
#endif

#ifndef PROBE_WORKGROUP_SIZE
#define PROBE_WORKGROUP_SIZE 8
#endif

layout(local_size_x = PROBE_WORKGROUP_SIZE, local_size_y = PROBE_WORKGROUP_SIZE) in;

layout(binding = 0) uniform sampler2D sourceFace;

layout(std430, binding = 1) writeonly buffer FaceTexels {
    vec4 texels[];
};

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    texels[texel.y * PROBE_FACE_SIZE + texel.x] = texelFetch(sourceFace, texel, 0);
}
