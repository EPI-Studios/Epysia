const int UV_MAPPING_MESH = 0;

vec2 mappedUv(vec2 uv) {
    return uv * material.uvScale.xy + material.uvOffset.xy;
}

vec3 triplanarPosition() {
    return vertexWorldPosition * material.uvScale + material.uvOffset;
}

vec3 triplanarWeights() {
    vec3 weights = pow(abs(normalize(vertexWorldNormal)), vec3(material.triplanarSharpness));
    return weights / max(weights.x + weights.y + weights.z, 1.0e-4);
}

vec4 materialTexture(sampler2D source, vec2 uv) {
    if (material.uvMapping == UV_MAPPING_MESH) {
        return texture(source, mappedUv(uv));
    }
    vec3 position = triplanarPosition();
    vec3 weights = triplanarWeights();
    return texture(source, position.zy) * weights.x
            + texture(source, position.xz) * weights.y
            + texture(source, position.xy) * weights.z;
}
