const int MAX_LIGHTS = 8;
const int MAX_CASCADES = 4;
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
    ivec4 lightCountAndShadowIndex; // .x = light count, .y = shadowed light index, .z = cascade count
    Light lights[MAX_LIGHTS];
} frame;

float frameTime() { return frame.cameraPosition.w; }

layout(std140, binding = 1) uniform ObjectUbo {
    mat4 model;
    mat4 normalMatrix;
} object;
