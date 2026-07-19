layout(binding = 0) uniform sampler2D ambientOcclusionSource;
layout(binding = 2) uniform sampler2D sceneDepth;

layout(std140, binding = 1) uniform SsaoUbo {
    mat4 viewProjection;
    mat4 inverseViewProjection;
    vec4 params;
    vec4 cameraDepth;
} ssao;

const int BLUR_RADIUS = 4;
const float GAUSSIAN_WEIGHTS[5] = float[](0.2270270270, 0.1945945946, 0.1216216216, 0.0540540541, 0.0162162162);
const float DEPTH_TOLERANCE_SCALE = 0.05;
const float DEPTH_TOLERANCE_MINIMUM = 0.05;

float linearizeBlurDepth(float ndcDepth) {
    float nearPlane = ssao.cameraDepth.x;
    float farPlane = ssao.cameraDepth.y;
    float clipZ = ndcDepth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - clipZ * (farPlane - nearPlane));
}

float blurAmbientOcclusion(vec2 uv, vec2 direction) {
    vec2 texelSize = 1.0 / vec2(textureSize(ambientOcclusionSource, 0));
    float centerDepth = linearizeBlurDepth(texture(sceneDepth, uv).r);
    float tolerance = centerDepth * DEPTH_TOLERANCE_SCALE + DEPTH_TOLERANCE_MINIMUM;
    float sum = texture(ambientOcclusionSource, uv).r * GAUSSIAN_WEIGHTS[0];
    float weightSum = GAUSSIAN_WEIGHTS[0];
    for (int tap = 1; tap <= BLUR_RADIUS; tap++) {
        for (int side = -1; side <= 1; side += 2) {
            vec2 offsetUv = uv + direction * texelSize * float(tap * side);
            float sampleDepth = linearizeBlurDepth(texture(sceneDepth, offsetUv).r);
            float weight = GAUSSIAN_WEIGHTS[tap] * exp(-abs(sampleDepth - centerDepth) / tolerance);
            sum += texture(ambientOcclusionSource, offsetUv).r * weight;
            weightSum += weight;
        }
    }
    return sum / max(weightSum, 1.0e-4);
}
