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
