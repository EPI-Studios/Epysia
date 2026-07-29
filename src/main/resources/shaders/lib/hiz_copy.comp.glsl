#version 430 core

layout(local_size_x = 8, local_size_y = 8) in;

layout(binding = 0) uniform sampler2D sceneDepth;
layout(binding = 1, r32f) uniform writeonly image2D targetLevel;

void main() {
    ivec2 target = ivec2(gl_GlobalInvocationID.xy);
    ivec2 targetSize = imageSize(targetLevel);
    if (target.x >= targetSize.x || target.y >= targetSize.y) {
        return;
    }
    ivec2 sourceSize = textureSize(sceneDepth, 0);
    ivec2 base = min(target, sourceSize - ivec2(1));
    imageStore(targetLevel, target, vec4(texelFetch(sceneDepth, base, 0).r));
}
