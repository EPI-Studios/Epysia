vec4 fogShade(vec3 worldPosition, float viewDistance, vec2 uv, float time) {
    float distanceBeyondStart = max(viewDistance - fogDistanceStart(), 0.0);
    float density = 1.0 - exp(-distanceBeyondStart * fogDistanceDensity());

    float bands = sin(worldPosition.x * 0.35 + time * 0.4)
            * sin(worldPosition.z * 0.27 - time * 0.31);
    float heightAbove = worldPosition.y - fogHeightOrigin();
    float height = exp(-max(heightAbove, 0.0) * fogHeightFalloff()) * fogHeightDensity();

    float factor = density * clamp(height + bands * 0.15, 0.0, 1.0);
    vec3 tint = fogTint() * (0.9 + bands * 0.1);
    return vec4(tint, clamp(factor, 0.0, 1.0));
}
