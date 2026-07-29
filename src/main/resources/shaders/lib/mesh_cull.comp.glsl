#version 430 core

layout(local_size_x = 64) in;

const uint PHASE_REDRAW_LAST_VISIBLE = 0u;
const uint PHASE_TEST_REMAINDER = 1u;

struct Instance {
    mat4 model;
    mat4 normalMatrix;
};

layout(std430, binding = 0) readonly buffer SourceInstances {
    Instance sourceInstances[];
};

layout(std430, binding = 1) writeonly buffer VisibleInstances {
    Instance visibleInstances[];
};

layout(std430, binding = 2) buffer IndirectArguments {
    uint indexCount;
    uint instanceCount;
    uint firstIndex;
    uint baseVertex;
    uint baseInstance;
};

layout(std430, binding = 3) buffer VisibilityState {
    uint lastVisible[];
};

layout(binding = 4) uniform sampler2D hiZPyramid;

layout(std140, binding = 5) uniform CullParameters {
    mat4 viewProjection;
    vec4 frustumPlanes[6];
    vec4 localBoundsMinimum;
    vec4 localBoundsMaximum;
    vec4 pyramidSizeAndLevels;
    uvec4 countAndPhase;
};

void worldBounds(mat4 model, out vec3 boundsMinimum, out vec3 boundsMaximum) {
    boundsMinimum = vec3(1.0e30);
    boundsMaximum = vec3(-1.0e30);
    for (int corner = 0; corner < 8; corner++) {
        vec3 local = vec3(
            (corner & 1) == 0 ? localBoundsMinimum.x : localBoundsMaximum.x,
            (corner & 2) == 0 ? localBoundsMinimum.y : localBoundsMaximum.y,
            (corner & 4) == 0 ? localBoundsMinimum.z : localBoundsMaximum.z);
        vec3 world = (model * vec4(local, 1.0)).xyz;
        boundsMinimum = min(boundsMinimum, world);
        boundsMaximum = max(boundsMaximum, world);
    }
}

bool insideFrustum(vec3 boundsMinimum, vec3 boundsMaximum) {
    for (int plane = 0; plane < 6; plane++) {
        vec3 positive = vec3(
            frustumPlanes[plane].x >= 0.0 ? boundsMaximum.x : boundsMinimum.x,
            frustumPlanes[plane].y >= 0.0 ? boundsMaximum.y : boundsMinimum.y,
            frustumPlanes[plane].z >= 0.0 ? boundsMaximum.z : boundsMinimum.z);
        if (dot(frustumPlanes[plane].xyz, positive) + frustumPlanes[plane].w < 0.0) {
            return false;
        }
    }
    return true;
}

bool occludedByPyramid(vec3 boundsMinimum, vec3 boundsMaximum) {
    vec3 screenMinimum = vec3(1.0e30);
    vec3 screenMaximum = vec3(-1.0e30);
    for (int corner = 0; corner < 8; corner++) {
        vec3 world = vec3(
            (corner & 1) == 0 ? boundsMinimum.x : boundsMaximum.x,
            (corner & 2) == 0 ? boundsMinimum.y : boundsMaximum.y,
            (corner & 4) == 0 ? boundsMinimum.z : boundsMaximum.z);
        vec4 clip = viewProjection * vec4(world, 1.0);
        if (clip.w <= 0.0) {
            return false;
        }
        vec3 ndc = clip.xyz / clip.w;
        screenMinimum = min(screenMinimum, ndc);
        screenMaximum = max(screenMaximum, ndc);
    }
    vec2 uvMinimum = screenMinimum.xy * 0.5 + 0.5;
    vec2 uvMaximum = screenMaximum.xy * 0.5 + 0.5;
    vec2 sizeInTexels = (uvMaximum - uvMinimum) * pyramidSizeAndLevels.xy;
    float level = clamp(ceil(log2(max(sizeInTexels.x, sizeInTexels.y))),
                        0.0, pyramidSizeAndLevels.z - 1.0);
    float farthest = textureLod(hiZPyramid, (uvMinimum + uvMaximum) * 0.5, level).r;
    if (farthest <= 0.0) {
        return false;
    }
    float nearest = screenMinimum.z * 0.5 + 0.5;
    return nearest > farthest;
}

void main() {
    uint instance = gl_GlobalInvocationID.x;
    if (instance >= countAndPhase.x) {
        return;
    }
    uint phase = countAndPhase.y;
    if (phase == PHASE_REDRAW_LAST_VISIBLE) {
        if (lastVisible[instance] == 0u) {
            return;
        }
        visibleInstances[atomicAdd(instanceCount, 1u)] = sourceInstances[instance];
        return;
    }
    vec3 boundsMinimum;
    vec3 boundsMaximum;
    worldBounds(sourceInstances[instance].model, boundsMinimum, boundsMaximum);
    bool visible = insideFrustum(boundsMinimum, boundsMaximum)
            && !occludedByPyramid(boundsMinimum, boundsMaximum);
    bool alreadyDrawn = lastVisible[instance] == 1u;
    lastVisible[instance] = visible ? 1u : 0u;
    if (visible && !alreadyDrawn) {
        visibleInstances[atomicAdd(instanceCount, 1u)] = sourceInstances[instance];
    }
}
