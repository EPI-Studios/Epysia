#version 430 core

layout(local_size_x = 8, local_size_y = 8) in;

layout(binding = 0, r32f) uniform readonly image2D sourceLevel;
layout(binding = 1, r32f) uniform writeonly image2D targetLevel;

void main() {
    ivec2 target = ivec2(gl_GlobalInvocationID.xy);
    ivec2 targetSize = imageSize(targetLevel);
    if (target.x >= targetSize.x || target.y >= targetSize.y) {
        return;
    }
    ivec2 sourceLimit = imageSize(sourceLevel) - ivec2(1);
    ivec2 base = target * 2;
    float nearest = imageLoad(sourceLevel, min(base, sourceLimit)).r;
    nearest = max(nearest, imageLoad(sourceLevel, min(base + ivec2(1, 0), sourceLimit)).r);
    nearest = max(nearest, imageLoad(sourceLevel, min(base + ivec2(0, 1), sourceLimit)).r);
    nearest = max(nearest, imageLoad(sourceLevel, min(base + ivec2(1, 1), sourceLimit)).r);
    imageStore(targetLevel, target, vec4(nearest));
}
