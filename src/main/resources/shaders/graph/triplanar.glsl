vec3 graphTriplanar(sampler2D source, vec3 position, vec3 normal, float scale) {
    vec3 weights = abs(normalize(normal + vec3(0.0, 1.0e-5, 0.0)));
    weights /= max(weights.x + weights.y + weights.z, 1.0e-4);
    vec3 scaled = position * scale;
    return texture(source, scaled.yz).rgb * weights.x
            + texture(source, scaled.xz).rgb * weights.y
            + texture(source, scaled.xy).rgb * weights.z;
}
