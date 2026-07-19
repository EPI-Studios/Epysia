const float PBR_PI = 3.14159265359;

float distributionGgx(float normalDotHalf, float roughness) {
    float alpha = roughness * roughness;
    float alphaSquared = alpha * alpha;
    float denominator = normalDotHalf * normalDotHalf * (alphaSquared - 1.0) + 1.0;
    return alphaSquared / max(PBR_PI * denominator * denominator, 1.0e-6);
}

float geometrySmith(float normalDotView, float normalDotLight, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    float viewTerm = normalDotView / (normalDotView * (1.0 - k) + k);
    float lightTerm = normalDotLight / (normalDotLight * (1.0 - k) + k);
    return viewTerm * lightTerm;
}

vec3 fresnelSchlick(float cosTheta, vec3 baseReflectivity) {
    return baseReflectivity + (1.0 - baseReflectivity) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 fresnelSchlickRoughness(float cosTheta, vec3 baseReflectivity, float roughness) {
    vec3 maxReflectivity = max(vec3(1.0 - roughness), baseReflectivity);
    return baseReflectivity + (maxReflectivity - baseReflectivity) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 cookTorranceBrdf(vec3 normal, vec3 viewDirection, vec3 toLight,
                      vec3 albedo, float metallic, float roughness,
                      vec3 radiance) {
    vec3 halfway = normalize(toLight + viewDirection);
    float normalDotView = max(dot(normal, viewDirection), 1.0e-4);
    float normalDotLight = max(dot(normal, toLight), 0.0);
    float normalDotHalf = max(dot(normal, halfway), 0.0);
    vec3 baseReflectivity = mix(vec3(0.04), albedo, metallic);
    float distribution = distributionGgx(normalDotHalf, roughness);
    float geometry = geometrySmith(normalDotView, normalDotLight, roughness);
    vec3 fresnel = fresnelSchlick(max(dot(halfway, viewDirection), 0.0), baseReflectivity);
    vec3 specular = (distribution * geometry * fresnel)
            / max(4.0 * normalDotView * normalDotLight, 1.0e-4);
    vec3 diffuseWeight = (vec3(1.0) - fresnel) * (1.0 - metallic);
    return (diffuseWeight * albedo / PBR_PI + specular) * radiance * normalDotLight;
}
