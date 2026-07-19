#version 430 core
in vec2 vertexUv;

layout(binding = 0) uniform sampler2D inputColor;

layout(std140, binding = 1) uniform PostUbo {
    vec4 vignetteParams;
    vec4 gradeParams;
    vec4 fogColor;
    vec4 fogDistance;
    vec4 cameraDepth;
    vec4 cameraPosition;
    mat4 inverseViewProjection;
    vec4 effectParams;
} post;

out vec4 outColor;

const float EDGE_THRESHOLD_MIN = 0.0312;
const float EDGE_THRESHOLD_MAX = 0.125;
const float SUBPIXEL_QUALITY = 0.75;
const int ITERATIONS = 12;
const float QUALITY[12] = float[](1.0, 1.0, 1.0, 1.0, 1.0, 1.5, 2.0, 2.0, 2.0, 2.0, 4.0, 8.0);

float lumaAt(vec2 uv) {
    vec3 color = texture(inputColor, uv).rgb;
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 centerColor = texture(inputColor, vertexUv).rgb;
    if (post.effectParams.z < 0.5) {
        outColor = vec4(centerColor, 1.0);
        return;
    }
    vec2 texelSize = 1.0 / vec2(textureSize(inputColor, 0));
    float lumaCenter = dot(centerColor, vec3(0.299, 0.587, 0.114));
    float lumaDown = lumaAt(vertexUv + vec2(0.0, -texelSize.y));
    float lumaUp = lumaAt(vertexUv + vec2(0.0, texelSize.y));
    float lumaLeft = lumaAt(vertexUv + vec2(-texelSize.x, 0.0));
    float lumaRight = lumaAt(vertexUv + vec2(texelSize.x, 0.0));

    float lumaMin = min(lumaCenter, min(min(lumaDown, lumaUp), min(lumaLeft, lumaRight)));
    float lumaMax = max(lumaCenter, max(max(lumaDown, lumaUp), max(lumaLeft, lumaRight)));
    float lumaRange = lumaMax - lumaMin;
    if (lumaRange < max(EDGE_THRESHOLD_MIN, lumaMax * EDGE_THRESHOLD_MAX)) {
        outColor = vec4(centerColor, 1.0);
        return;
    }

    float lumaDownLeft = lumaAt(vertexUv + vec2(-texelSize.x, -texelSize.y));
    float lumaUpRight = lumaAt(vertexUv + vec2(texelSize.x, texelSize.y));
    float lumaUpLeft = lumaAt(vertexUv + vec2(-texelSize.x, texelSize.y));
    float lumaDownRight = lumaAt(vertexUv + vec2(texelSize.x, -texelSize.y));

    float lumaDownUp = lumaDown + lumaUp;
    float lumaLeftRight = lumaLeft + lumaRight;
    float lumaLeftCorners = lumaDownLeft + lumaUpLeft;
    float lumaDownCorners = lumaDownLeft + lumaDownRight;
    float lumaRightCorners = lumaDownRight + lumaUpRight;
    float lumaUpCorners = lumaUpRight + lumaUpLeft;

    float edgeHorizontal = abs(-2.0 * lumaLeft + lumaLeftCorners)
            + abs(-2.0 * lumaCenter + lumaDownUp) * 2.0
            + abs(-2.0 * lumaRight + lumaRightCorners);
    float edgeVertical = abs(-2.0 * lumaUp + lumaUpCorners)
            + abs(-2.0 * lumaCenter + lumaLeftRight) * 2.0
            + abs(-2.0 * lumaDown + lumaDownCorners);
    bool isHorizontal = edgeHorizontal >= edgeVertical;

    float luma1 = isHorizontal ? lumaDown : lumaLeft;
    float luma2 = isHorizontal ? lumaUp : lumaRight;
    float gradient1 = luma1 - lumaCenter;
    float gradient2 = luma2 - lumaCenter;
    bool isSteepest1 = abs(gradient1) >= abs(gradient2);
    float gradientScaled = 0.25 * max(abs(gradient1), abs(gradient2));

    float stepLength = isHorizontal ? texelSize.y : texelSize.x;
    float lumaLocalAverage;
    if (isSteepest1) {
        stepLength = -stepLength;
        lumaLocalAverage = 0.5 * (luma1 + lumaCenter);
    } else {
        lumaLocalAverage = 0.5 * (luma2 + lumaCenter);
    }
    vec2 currentUv = vertexUv;
    if (isHorizontal) {
        currentUv.y += stepLength * 0.5;
    } else {
        currentUv.x += stepLength * 0.5;
    }

    vec2 offset = isHorizontal ? vec2(texelSize.x, 0.0) : vec2(0.0, texelSize.y);
    vec2 uv1 = currentUv - offset;
    vec2 uv2 = currentUv + offset;
    float lumaEnd1 = lumaAt(uv1) - lumaLocalAverage;
    float lumaEnd2 = lumaAt(uv2) - lumaLocalAverage;
    bool reached1 = abs(lumaEnd1) >= gradientScaled;
    bool reached2 = abs(lumaEnd2) >= gradientScaled;
    if (!reached1) uv1 -= offset;
    if (!reached2) uv2 += offset;

    if (!(reached1 && reached2)) {
        for (int i = 2; i < ITERATIONS; i++) {
            if (!reached1) {
                lumaEnd1 = lumaAt(uv1) - lumaLocalAverage;
                reached1 = abs(lumaEnd1) >= gradientScaled;
            }
            if (!reached2) {
                lumaEnd2 = lumaAt(uv2) - lumaLocalAverage;
                reached2 = abs(lumaEnd2) >= gradientScaled;
            }
            if (reached1 && reached2) break;
            if (!reached1) uv1 -= offset * QUALITY[i];
            if (!reached2) uv2 += offset * QUALITY[i];
        }
    }

    float distance1 = isHorizontal ? (vertexUv.x - uv1.x) : (vertexUv.y - uv1.y);
    float distance2 = isHorizontal ? (uv2.x - vertexUv.x) : (uv2.y - vertexUv.y);
    bool isDirection1 = distance1 < distance2;
    float distanceFinal = min(distance1, distance2);
    float edgeThickness = distance1 + distance2;
    float pixelOffset = -distanceFinal / max(edgeThickness, 1.0e-6) + 0.5;

    bool isLumaCenterSmaller = lumaCenter < lumaLocalAverage;
    bool correctVariation = ((isDirection1 ? lumaEnd1 : lumaEnd2) < 0.0) != isLumaCenterSmaller;
    float finalOffset = correctVariation ? pixelOffset : 0.0;

    float lumaAverage = (1.0 / 12.0) * (2.0 * (lumaDownUp + lumaLeftRight) + lumaLeftCorners + lumaRightCorners);
    float subPixelOffset1 = clamp(abs(lumaAverage - lumaCenter) / max(lumaRange, 1.0e-6), 0.0, 1.0);
    float subPixelOffset2 = (-2.0 * subPixelOffset1 + 3.0) * subPixelOffset1 * subPixelOffset1;
    float subPixelOffsetFinal = subPixelOffset2 * subPixelOffset2 * SUBPIXEL_QUALITY;
    finalOffset = max(finalOffset, subPixelOffsetFinal);

    vec2 finalUv = vertexUv;
    if (isHorizontal) {
        finalUv.y += finalOffset * stepLength;
    } else {
        finalUv.x += finalOffset * stepLength;
    }
    outColor = vec4(texture(inputColor, finalUv).rgb, 1.0);
}
