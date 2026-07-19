vec3 skyRadiance(vec3 direction, vec3 sunDirection, float intensity) {
    float up = clamp(direction.y, -1.0, 1.0);
    float sunHeight = clamp(sunDirection.y, 0.0, 1.0);
    vec3 dayZenith = vec3(0.18, 0.32, 0.62);
    vec3 dayHorizon = vec3(0.58, 0.66, 0.78);
    vec3 duskHorizon = vec3(0.85, 0.48, 0.28);
    float duskFactor = 1.0 - smoothstep(0.0, 0.35, sunHeight);
    vec3 zenith = dayZenith * mix(1.0, 0.35, duskFactor);
    vec3 horizon = mix(dayHorizon, duskHorizon, duskFactor);
    float horizonBlend = pow(1.0 - clamp(up, 0.0, 1.0), 3.0);
    vec3 sky = mix(zenith, horizon, horizonBlend);
    float sunAmount = max(dot(direction, sunDirection), 0.0);
    sky += vec3(1.0, 0.94, 0.82) * pow(sunAmount, 2200.0) * 60.0;
    sky += vec3(1.0, 0.82, 0.60) * pow(sunAmount, 10.0) * 0.22;
    vec3 ground = vec3(0.16, 0.15, 0.14) * (0.4 + 0.6 * clamp(1.0 + up, 0.0, 1.0));
    ground *= mix(1.0, 0.35, duskFactor);
    return mix(ground, sky, smoothstep(-0.04, 0.04, up)) * intensity;
}
