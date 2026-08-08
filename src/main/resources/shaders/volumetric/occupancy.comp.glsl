#version 430 core
#include "volumetric/volumetric_common.glsl"

layout(local_size_x = 128) in;

layout(std430, binding = 2) buffer OccupancyBuffer {
    int occupancy[];
};

struct OccluderShape {
    mat4 worldToLocal;
    vec4 halfExtents;
    vec4 extra;
};

layout(std430, binding = 3) readonly buffer ShapeBuffer {
    OccluderShape shapes[];
};

const int KIND_BOX = 0;
const int KIND_SPHERE = 1;
const int KIND_CAPSULE = 2;

bool insideBox(vec3 local, vec3 halfExtents, float widening) {
    return all(lessThanEqual(abs(local), halfExtents + vec3(widening)));
}

bool insideSphere(vec3 local, float radius, float widening) {
    return dot(local, local) <= (radius + widening) * (radius + widening);
}

bool insideCapsule(vec3 local, float radius, float halfHeight, float widening) {
    vec3 clamped = vec3(local.x, local.y - clamp(local.y, -halfHeight, halfHeight), local.z);
    return dot(clamped, clamped) <= (radius + widening) * (radius + widening);
}

bool occludedBy(OccluderShape shape, vec3 worldPosition, float widening) {
    vec3 local = (shape.worldToLocal * vec4(worldPosition, 1.0)).xyz;
    int kind = int(shape.extra.y);
    if (kind == KIND_SPHERE) {
        return insideSphere(local, shape.halfExtents.w, widening);
    }
    if (kind == KIND_CAPSULE) {
        return insideCapsule(local, shape.halfExtents.w, shape.extra.x, widening);
    }
    return insideBox(local, shape.halfExtents.xyz, widening);
}

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(VOXEL_COUNT)) {
        return;
    }
    occupancy[index] = 0;
    vec3 local = voxelCenterLocal(toVoxelPosition(index));
    vec3 worldPosition = (volumeLocalToWorld * vec4(local, 1.0)).xyz;
    float widening = VOXEL_SIZE * 0.5;
    for (int shapeIndex = 0; shapeIndex < SHAPE_COUNT; ++shapeIndex) {
        if (occludedBy(shapes[shapeIndex], worldPosition, widening)) {
            occupancy[index] = 1;
            return;
        }
    }
}
