const int MAX_CASCADES = 4;
const int MAX_SHADOW_SPOTS = 8;
const int MAX_SHADOW_POINTS = 4;
const int LIGHT_TYPE_DIRECTIONAL = 0;
const int LIGHT_TYPE_POINT = 1;
const int LIGHT_TYPE_SPOT = 2;

struct Light {
    vec4 positionAndType;
    vec4 directionAndRange;
    vec4 colorAndIntensity;
    vec4 spotCones;
};

layout(std140, binding = 0) uniform FrameUbo {
    mat4 cameraViewProjection;
    mat4 cascadeViewProjection[MAX_CASCADES];
    vec4 cascadeSplits;
    vec4 cascadeTexelSizes;
    vec4 ambientColor;          // rgb = ambient tint, a = ambient intensity
    vec4 cameraPosition;        // .xyz = world camera pos, .w = elapsed time in seconds (engine global)
    ivec4 lightCountAndShadowIndex; // .x = light count, .y = shadowed light index, .z = cascade count, .w = directional count
    ivec4 spotShadowMeta;           // .x = spot shadow caster count
    mat4 spotShadowViewProjection[MAX_SHADOW_SPOTS];
    ivec4 pointShadowMeta;          // .x = point shadow caster count
    mat4 pointShadowViewProjection[MAX_SHADOW_POINTS * 6];
    ivec4 clusterGrid;              // .xyz = grid dims, .w = culling enabled
    vec4 clusterParams;            // .x = zNear, .y = zFar, .z = screenWidth, .w = screenHeight
    vec4 clusterSliceParams;       // .x = sliceScale, .y = sliceBias, .z = maxLightsPerCluster
} frame;

float frameTime() { return frame.cameraPosition.w; }

struct InstanceTransform {
    mat4 model;
    mat4 normalMatrix;
};

// One storage buffer feeds both single-object and instanced draws: a single-object draw
// binds a one-element buffer and reads element 0. Sharing one code path keeps the compiled
// program identical either way, so batching an object can never shift its pixels.
layout(std430, binding = 3) readonly buffer InstanceTransformSsbo {
    InstanceTransform instanceTransforms[];
};

#define OBJECT_MODEL (instanceTransforms[gl_InstanceID].model)
#define OBJECT_NORMAL_MATRIX (instanceTransforms[gl_InstanceID].normalMatrix)

// Single-object view of the same transform. Fragment stages have no gl_InstanceID, so the
// surface-shader object helpers read this instead; materials whose surface shader uses those
// helpers are kept on the per-object path, where this buffer describes exactly that object.
layout(std140, binding = 1) uniform ObjectUbo {
    mat4 model;
    mat4 normalMatrix;
} object;
