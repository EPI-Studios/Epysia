const int LIGHT2D_TYPE_POINT = 0;
const int LIGHT2D_TYPE_SPOT = 1;
const int LIGHT2D_TYPE_GLOBAL = 2;

struct Light2d {
    vec4 positionHeightRange;
    vec4 colorAndIntensity;
    vec4 directionAndCones;
    vec4 shapeParameters;
    ivec4 typeAndLayers;
};

layout(std430, binding = 6) readonly buffer Light2dBuffer {
    ivec4 light2dHeader;
    Light2d lights2d[];
};

int light2dCount() {
    return light2dHeader.x;
}

bool light2dAffects(Light2d light, int surfaceLayers) {
    return (light.typeAndLayers.y & surfaceLayers) != 0;
}

float light2dRadialFalloff(float distanceToLight, float innerRadius, float range) {
    float span = max(range - innerRadius, 1.0e-4);
    float normalized = clamp((distanceToLight - innerRadius) / span, 0.0, 1.0);
    float inverted = 1.0 - normalized;
    return inverted * inverted;
}

float light2dSpotFactor(Light2d light, vec2 lightToSurface) {
    float planarLength = length(lightToSurface);
    if (planarLength < 1.0e-5) {
        return 1.0;
    }
    float cosAngle = dot(lightToSurface / planarLength, normalize(light.directionAndCones.xy));
    return smoothstep(light.directionAndCones.z, light.directionAndCones.w, cosAngle);
}

bool unpackLight2d(Light2d light,
                   vec3 surfacePosition,
                   out vec3 toLight,
                   out vec3 radiance) {
    int type = light.typeAndLayers.x;
    vec3 lightColor = light.colorAndIntensity.rgb * light.colorAndIntensity.a;
    if (type == LIGHT2D_TYPE_GLOBAL) {
        toLight = -normalize(light.directionAndCones.xyz);
        radiance = lightColor;
        return true;
    }
    vec3 lightPosition = light.positionHeightRange.xyz;
    vec3 delta = lightPosition - surfacePosition;
    float distanceToLight = length(delta);
    float range = light.positionHeightRange.w;
    if (distanceToLight > range) {
        return false;
    }
    toLight = delta / max(distanceToLight, 1.0e-5);
    float attenuation = light2dRadialFalloff(distanceToLight, light.shapeParameters.x, range);
    if (type == LIGHT2D_TYPE_SPOT) {
        attenuation *= light2dSpotFactor(light, -delta.xy);
    }
    radiance = lightColor * attenuation;
    return attenuation > 0.0;
}
