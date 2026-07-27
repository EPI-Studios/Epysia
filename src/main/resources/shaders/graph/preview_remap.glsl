vec3 graphPreviewRemap(vec3 value) {
    vec3 centered = value * 0.5 + 0.5;
    bool hasNegative = min(value.x, min(value.y, value.z)) < -0.002;
    vec3 shown = hasNegative ? centered : value;
    float peak = max(shown.r, max(shown.g, shown.b));
    float boost = (peak > 0.0 && peak < 0.25) ? 0.25 / peak : 1.0;
    return clamp(shown * boost, 0.0, 1.0);
}
