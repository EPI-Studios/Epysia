layout(std140, binding = 0) uniform CaptureUbo {
    vec4 faceForward;
    vec4 faceRight;
    vec4 faceUp;
    vec4 sunDirectionAndIntensity;
    vec4 params;
} capture;

vec3 captureDirection(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    return normalize(capture.faceForward.xyz
            + ndc.x * capture.faceRight.xyz
            + ndc.y * capture.faceUp.xyz);
}

vec2 hammersley(uint index, uint count) {
    uint bits = index;
    bits = (bits << 16u) | (bits >> 16u);
    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
    float radicalInverse = float(bits) * 2.3283064365386963e-10;
    return vec2(float(index) / float(count), radicalInverse);
}

vec3 importanceSampleGgx(vec2 randomPair, vec3 normal, float roughness) {
    float alpha = roughness * roughness;
    float phi = 6.28318530718 * randomPair.x;
    float cosTheta = sqrt((1.0 - randomPair.y) / (1.0 + (alpha * alpha - 1.0) * randomPair.y));
    float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
    vec3 halfway = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);
    vec3 upAxis = abs(normal.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(upAxis, normal));
    vec3 bitangent = cross(normal, tangent);
    return normalize(tangent * halfway.x + bitangent * halfway.y + normal * halfway.z);
}
