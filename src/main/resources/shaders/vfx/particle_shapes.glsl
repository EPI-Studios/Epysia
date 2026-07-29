struct ShapeSample {
    vec3 position;
    vec3 direction;
};

const float SHAPE_TWO_PI = 6.28318530718;
const vec3 SHAPE_DEFAULT_DIRECTION = vec3(0.0, 1.0, 0.0);

float shapeRandom(uint key, uint stream) {
    return hashFloat(key * 6364136u + stream * 2654435u + 1013904u);
}

float shapeSign(uint key, uint stream) {
    return shapeRandom(key, stream) < 0.5 ? -1.0 : 1.0;
}

vec3 shapeSafeNormalize(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 1e-12 ? value * inversesqrt(lengthSquared) : fallback;
}

float shapeRadialDistance(float radius, float radiusThickness, float unitRandom) {
    float outer = max(radius, 0.0);
    float inner = (1.0 - clamp(radiusThickness, 0.0, 1.0)) * outer;
    float origin = inner * inner;
    float bound = outer * outer;
    return sqrt(origin + unitRandom * (bound - origin));
}

float shapeVolumeDistance(float radius, float radiusThickness, float unitRandom) {
    float outer = max(radius, 0.0);
    float inner = (1.0 - clamp(radiusThickness, 0.0, 1.0)) * outer;
    float origin = inner * inner * inner;
    float bound = outer * outer * outer;
    return pow(origin + unitRandom * (bound - origin), 1.0 / 3.0);
}

float shapeArcAngle(float arcDegrees, float unitRandom) {
    return radians(clamp(arcDegrees, 0.0, 360.0)) * unitRandom;
}

ShapeSample shapeCone(float radius, float radiusThickness, float arcDegrees, float angleDegrees, uint key) {
    float distanceFromAxis = shapeRadialDistance(radius, radiusThickness, shapeRandom(key, 1u));
    float sweepAngle = shapeArcAngle(arcDegrees, shapeRandom(key, 2u));
    float normalizedRadius = radius > 0.0 ? distanceFromAxis / radius : sqrt(shapeRandom(key, 3u));
    float spreadAngle = normalizedRadius * radians(angleDegrees);
    float spreadSine = sin(spreadAngle);
    ShapeSample result;
    result.position = vec3(distanceFromAxis * cos(sweepAngle), 0.0, distanceFromAxis * sin(sweepAngle));
    result.direction = shapeSafeNormalize(vec3(spreadSine * cos(sweepAngle),
            cos(spreadAngle),
            spreadSine * sin(sweepAngle)), SHAPE_DEFAULT_DIRECTION);
    return result;
}

ShapeSample shapeSphere(float radius, float radiusThickness, uint key) {
    float distanceFromCenter = shapeVolumeDistance(radius, radiusThickness, shapeRandom(key, 4u));
    float polarCosine = shapeRandom(key, 5u) * 2.0 - 1.0;
    float polarSine = sqrt(max(0.0, 1.0 - polarCosine * polarCosine));
    float azimuth = shapeRandom(key, 6u) * SHAPE_TWO_PI;
    vec3 unitPoint = vec3(polarSine * cos(azimuth), polarCosine, polarSine * sin(azimuth));
    ShapeSample result;
    result.position = unitPoint * distanceFromCenter;
    result.direction = shapeSafeNormalize(unitPoint, SHAPE_DEFAULT_DIRECTION);
    return result;
}

ShapeSample shapeHemisphere(float radius, float radiusThickness, uint key) {
    float distanceFromCenter = shapeVolumeDistance(radius, radiusThickness, shapeRandom(key, 7u));
    float polarCosine = shapeRandom(key, 8u);
    float polarSine = sqrt(max(0.0, 1.0 - polarCosine * polarCosine));
    float azimuth = shapeRandom(key, 9u) * SHAPE_TWO_PI;
    vec3 unitPoint = vec3(polarSine * cos(azimuth), polarCosine, polarSine * sin(azimuth));
    ShapeSample result;
    result.position = unitPoint * distanceFromCenter;
    result.direction = shapeSafeNormalize(unitPoint, SHAPE_DEFAULT_DIRECTION);
    return result;
}

ShapeSample shapeBox(vec3 halfExtents, float thickness, uint key) {
    vec3 outer = abs(halfExtents);
    vec3 inner = (1.0 - clamp(thickness, 0.0, 1.0)) * outer;
    vec3 point = vec3(mix(-outer.x, outer.x, shapeRandom(key, 10u)),
            mix(-outer.y, outer.y, shapeRandom(key, 11u)),
            mix(-outer.z, outer.z, shapeRandom(key, 12u)));
    float areaAlongX = outer.y * outer.z;
    float areaAlongY = outer.x * outer.z;
    float areaAlongZ = outer.x * outer.y;
    float areaTotal = areaAlongX + areaAlongY + areaAlongZ;
    ShapeSample result;
    result.direction = SHAPE_DEFAULT_DIRECTION;
    if (areaTotal <= 0.0) {
        result.position = point;
        return result;
    }
    float faceChoice = shapeRandom(key, 13u) * areaTotal;
    float bandFraction = shapeRandom(key, 14u);
    if (faceChoice < areaAlongX) {
        point.x = mix(inner.x, outer.x, bandFraction) * shapeSign(key, 15u);
    } else if (faceChoice < areaAlongX + areaAlongY) {
        point.y = mix(inner.y, outer.y, bandFraction) * shapeSign(key, 16u);
    } else {
        point.z = mix(inner.z, outer.z, bandFraction) * shapeSign(key, 17u);
    }
    result.position = point;
    return result;
}

ShapeSample shapeCircle(float radius, float radiusThickness, float arcDegrees, uint key) {
    float distanceFromCenter = shapeRadialDistance(radius, radiusThickness, shapeRandom(key, 18u));
    float sweepAngle = shapeArcAngle(arcDegrees, shapeRandom(key, 19u));
    vec3 radialDirection = vec3(cos(sweepAngle), 0.0, sin(sweepAngle));
    ShapeSample result;
    result.position = radialDirection * distanceFromCenter;
    result.direction = radialDirection;
    return result;
}

ShapeSample shapeCylinder(float radius, float radiusThickness, float height, float arcDegrees, uint key) {
    float distanceFromAxis = shapeRadialDistance(radius, radiusThickness, shapeRandom(key, 20u));
    float sweepAngle = shapeArcAngle(arcDegrees, shapeRandom(key, 21u));
    float verticalOffset = (shapeRandom(key, 22u) - 0.5) * height;
    vec3 radialDirection = vec3(cos(sweepAngle), 0.0, sin(sweepAngle));
    ShapeSample result;
    result.position = vec3(radialDirection.x * distanceFromAxis, verticalOffset,
            radialDirection.z * distanceFromAxis);
    result.direction = radialDirection;
    return result;
}

ShapeSample shapeDot(uint key) {
    ShapeSample result;
    result.position = vec3(0.0);
    result.direction = SHAPE_DEFAULT_DIRECTION;
    return result;
}

ShapeSample shapeEdge(float edgeLength, uint key) {
    ShapeSample result;
    result.position = vec3((shapeRandom(key, 23u) - 0.5) * edgeLength, 0.0, 0.0);
    result.direction = SHAPE_DEFAULT_DIRECTION;
    return result;
}

ShapeSample shapeToScreenPlane(ShapeSample source) {
    ShapeSample rotated;
    rotated.position = vec3(source.position.x, source.position.z, 0.0);
    rotated.direction = shapeSafeNormalize(vec3(source.direction.x, source.direction.z, 0.0),
            vec3(0.0, 1.0, 0.0));
    return rotated;
}
