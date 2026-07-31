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
    vec4 probeGridOrigin;          // .xyz = world position of probe (0,0,0)
    vec4 probeGridSpacing;         // .xyz = world distance between neighboring probes
    ivec4 probeGridResolution;     // .xyz = probe counts per axis
    mat4 cameraInverseViewProjection;
} frame;

float frameTime() { return frame.cameraPosition.w; }

struct InstanceTransform {
    mat4 model;
    mat4 normalMatrix;
};

layout(std430, binding = 3) readonly buffer InstanceTransformSsbo {
    InstanceTransform instanceTransforms[];
};

#ifdef MULTI_DRAW
layout(location = 7) in uint inDrawIndex;
#define OBJECT_MODEL (instanceTransforms[inDrawIndex].model)
#define OBJECT_INSTANCE_INDEX (int(inDrawIndex))
#define OBJECT_NORMAL_MATRIX (instanceTransforms[inDrawIndex].normalMatrix)
#else
#define OBJECT_MODEL (instanceTransforms[gl_InstanceID].model)
#define OBJECT_INSTANCE_INDEX (gl_InstanceID)
#define OBJECT_NORMAL_MATRIX (instanceTransforms[gl_InstanceID].normalMatrix)
#endif

layout(std140, binding = 1) uniform ObjectUbo {
    mat4 model;
    mat4 normalMatrix;
} object;
