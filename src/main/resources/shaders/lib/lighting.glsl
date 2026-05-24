layout(binding = 3) uniform sampler2DShadow shadowMap;

float sampleShadow(vec4 lightSpacePosition) {
    vec3 projected = lightSpacePosition.xyz / lightSpacePosition.w;
    projected = projected * 0.5 + 0.5;
    if (projected.z > 1.0) {
        return 1.0;
    }
    float bias = 0.0025;
    vec2 texelSize = 1.0 / vec2(textureSize(shadowMap, 0));
    float result = 0.0;
    result += texture(shadowMap, vec3(projected.xy + vec2(-0.5, -0.5) * texelSize, projected.z - bias));
    result += texture(shadowMap, vec3(projected.xy + vec2( 0.5, -0.5) * texelSize, projected.z - bias));
    result += texture(shadowMap, vec3(projected.xy + vec2(-0.5,  0.5) * texelSize, projected.z - bias));
    result += texture(shadowMap, vec3(projected.xy + vec2( 0.5,  0.5) * texelSize, projected.z - bias));
    return result * 0.25;
}

void unpackLight(Light light,
                 vec3 worldPosition,
                 out vec3 toLight,
                 out vec3 lightRadiance,
                 out float attenuation) {
    int type = int(light.positionAndType.w);
    vec3 lightColor = light.colorAndIntensity.rgb * light.colorAndIntensity.a;
    if (type == LIGHT_TYPE_DIRECTIONAL) {
        toLight = -normalize(light.directionAndRange.xyz);
        lightRadiance = lightColor;
        attenuation = 1.0;
        return;
    }
    vec3 lightPosition = light.positionAndType.xyz;
    vec3 delta = lightPosition - worldPosition;
    float distance = length(delta);
    toLight = delta / max(distance, 1.0e-5);
    float range = max(light.directionAndRange.w, 1.0e-3);
    float falloff = clamp(1.0 - distance / range, 0.0, 1.0);
    attenuation = falloff * falloff;
    if (type == LIGHT_TYPE_SPOT) {
        float cosAngle = dot(-toLight, normalize(light.directionAndRange.xyz));
        attenuation *= smoothstep(light.spotCones.y, light.spotCones.x, cosAngle);
    }
    lightRadiance = lightColor;
}
