const int MAX_LIGHTS = 8;
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
    mat4 lightViewProjection;
    vec4 ambientColor;
    vec4 cameraPosition;
    ivec4 lightCountAndShadowIndex;
    Light lights[MAX_LIGHTS];
} frame;

layout(std140, binding = 1) uniform ObjectUbo {
    mat4 model;
} object;
