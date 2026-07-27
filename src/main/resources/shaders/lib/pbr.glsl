const float PBR_PI = 3.14159265359;
const float SPECULAR_AA_VARIANCE = 0.25;
const float SPECULAR_AA_THRESHOLD = 0.18;

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

float filteredRoughness(vec3 worldNormal, float roughness) {
    vec3 normalHorizontal = dFdx(worldNormal);
    vec3 normalVertical = dFdy(worldNormal);
    float variance = SPECULAR_AA_VARIANCE
            * (dot(normalHorizontal, normalHorizontal) + dot(normalVertical, normalVertical));
    float alpha = roughness * roughness;
    float widened = min(alpha + min(2.0 * variance, SPECULAR_AA_THRESHOLD), 1.0);
    return sqrt(widened);
}

vec3 sphereClosestPoint(vec3 normal, vec3 viewDirection, vec3 lightVector, float sourceRadius) {
    vec3 reflected = reflect(-viewDirection, normal);
    vec3 centerToRay = dot(lightVector, reflected) * reflected - lightVector;
    float centerDistance = length(centerToRay);
    return lightVector + centerToRay * clamp(sourceRadius / max(centerDistance, 1.0e-5), 0.0, 1.0);
}

float sphereEnergyNormalization(float roughness, float sourceRadius, float lightDistance) {
    float alpha = roughness * roughness;
    float widened = clamp(alpha + sourceRadius / (2.0 * max(lightDistance, 1.0e-4)), 0.0, 1.0);
    float ratio = alpha / max(widened, 1.0e-5);
    return ratio * ratio;
}

vec3 specularLobe(vec3 normal, vec3 viewDirection, vec3 toLight, float normalDotView,
                  vec3 baseReflectivity, float roughness) {
    vec3 halfway = normalize(toLight + viewDirection);
    float normalDotLight = max(dot(normal, toLight), 0.0);
    float normalDotHalf = max(dot(normal, halfway), 0.0);
    float distribution = distributionGgx(normalDotHalf, roughness);
    float geometry = geometrySmith(normalDotView, normalDotLight, roughness);
    vec3 fresnel = fresnelSchlick(max(dot(halfway, viewDirection), 0.0), baseReflectivity);
    return (distribution * geometry * fresnel)
            / max(4.0 * normalDotView * normalDotLight, 1.0e-4) * normalDotLight;
}

vec3 diffuseLobe(vec3 normal, vec3 viewDirection, vec3 toLight, vec3 albedo, float metallic,
                 vec3 baseReflectivity) {
    vec3 halfway = normalize(toLight + viewDirection);
    vec3 fresnel = fresnelSchlick(max(dot(halfway, viewDirection), 0.0), baseReflectivity);
    vec3 diffuseWeight = (vec3(1.0) - fresnel) * (1.0 - metallic);
    return diffuseWeight * albedo / PBR_PI * max(dot(normal, toLight), 0.0);
}

vec3 cookTorranceSphereBrdf(vec3 normal, vec3 viewDirection, vec3 toLight,
                            float lightDistance, float sourceRadius,
                            vec3 albedo, float metallic, float roughness,
                            vec3 radiance) {
    vec3 baseReflectivity = mix(vec3(0.04), albedo, metallic);
    float normalDotView = max(dot(normal, viewDirection), 1.0e-4);
    vec3 diffuse = diffuseLobe(normal, viewDirection, toLight, albedo, metallic, baseReflectivity);
    if (sourceRadius <= 0.0 || lightDistance <= 0.0) {
        return (diffuse + specularLobe(normal, viewDirection, toLight, normalDotView,
                baseReflectivity, roughness)) * radiance;
    }
    vec3 closestPoint = sphereClosestPoint(normal, viewDirection, toLight * lightDistance, sourceRadius);
    vec3 representative = normalize(closestPoint);
    vec3 specular = specularLobe(normal, viewDirection, representative, normalDotView,
            baseReflectivity, roughness);
    specular *= sphereEnergyNormalization(roughness, sourceRadius, lightDistance);
    return (diffuse + specular) * radiance;
}

vec3 cookTorranceBrdf(vec3 normal, vec3 viewDirection, vec3 toLight,
                      vec3 albedo, float metallic, float roughness,
                      vec3 radiance) {
    return cookTorranceSphereBrdf(normal, viewDirection, toLight, 0.0, 0.0,
            albedo, metallic, roughness, radiance);
}
