const float LIGHT_MINIMUM_DISTANCE_SQUARED = 0.01;

float windowedInverseSquare(float distance, float range) {
    float normalized = distance / max(range, 1.0e-3);
    float squared = normalized * normalized;
    float window = clamp(1.0 - squared * squared, 0.0, 1.0);
    return (window * window) / max(distance * distance, LIGHT_MINIMUM_DISTANCE_SQUARED);
}

void unpackLight(Light light,
                 vec3 worldPosition,
                 out vec3 toLight,
                 out vec3 lightRadiance,
                 out float attenuation,
                 out float sourceRadius,
                 out float lightDistance) {
    int type = int(light.positionAndType.w);
    vec3 lightColor = light.colorAndIntensity.rgb * light.colorAndIntensity.a;
    sourceRadius = max(light.spotCones.w, 0.0);
    if (type == LIGHT_TYPE_DIRECTIONAL) {
        toLight = -normalize(light.directionAndRange.xyz);
        lightRadiance = lightColor;
        attenuation = 1.0;
        lightDistance = 0.0;
        return;
    }
    vec3 lightPosition = light.positionAndType.xyz;
    vec3 delta = lightPosition - worldPosition;
    lightDistance = length(delta);
    toLight = delta / max(lightDistance, 1.0e-5);
    attenuation = windowedInverseSquare(lightDistance, light.directionAndRange.w);
    if (type == LIGHT_TYPE_SPOT) {
        float cosAngle = dot(-toLight, normalize(light.directionAndRange.xyz));
        attenuation *= smoothstep(light.spotCones.y, light.spotCones.x, cosAngle);
    }
    lightRadiance = lightColor;
}
