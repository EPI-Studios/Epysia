#version 430 core
#include "volumetric/volumetric_common.glsl"

layout(local_size_x = 8, local_size_y = 8) in;

layout(std430, binding = 4) readonly buffer DensityBuffer {
    int density[];
};

struct Deformer {
    vec4 originAndStartRadius;
    vec4 directionAndEndRadius;
};

layout(std430, binding = 8) readonly buffer DeformerBuffer {
    Deformer deformers[];
};

layout(binding = 6) uniform sampler3D noiseVolume;
layout(binding = 7) uniform sampler2D sceneDepth;

layout(rgba16f, binding = 2) uniform writeonly image2D scatteredColor;
layout(r16f, binding = 3) uniform writeonly image2D transmittance;

const float COARSE_STEP = 0.4;
const float MAX_DISTANCE = 200.0;

int voxelAt(vec3 worldPosition) {
    if (!insideVolume(worldPosition)) {
        return 0;
    }
    vec3 voxelSpace = worldToVoxelSpace(worldPosition);
    ivec3 resolution = ivec3(voxelResolution.xyz);
    ivec3 clamped = clamp(ivec3(voxelSpace), ivec3(0), resolution - 1);
    return density[toVoxelIndex(uvec3(clamped))];
}

float trilinearDensity(vec3 worldPosition) {
    if (!insideVolume(worldPosition)) {
        return 0.0;
    }
    vec3 voxelSpace = worldToVoxelSpace(worldPosition) - 0.5;
    ivec3 base = ivec3(floor(voxelSpace));
    vec3 fraction = voxelSpace - vec3(base);
    ivec3 resolution = ivec3(voxelResolution.xyz);
    float accumulated = 0.0;
    for (int x = 0; x < 2; ++x) {
        float weightX = mix(1.0 - fraction.x, fraction.x, float(x));
        for (int y = 0; y < 2; ++y) {
            float weightY = mix(1.0 - fraction.y, fraction.y, float(y));
            for (int z = 0; z < 2; ++z) {
                float weightZ = mix(1.0 - fraction.z, fraction.z, float(z));
                ivec3 position = clamp(base + ivec3(x, y, z), ivec3(0), resolution - 1);
                accumulated += weightX * weightY * weightZ * float(density[toVoxelIndex(uvec3(position))]);
            }
        }
    }
    return accumulated;
}

float sampleNoise(vec3 worldPosition) {
    vec3 coordinate = worldPosition / DETAIL_SCALE + animationDirection.xyz * FRAME_TIME;
    return texture(noiseVolume, coordinate).r;
}

float roundedConeDistance(Deformer deformer, vec3 position) {
    vec3 start = deformer.originAndStartRadius.xyz;
    vec3 end = start + DEFORMER_DEPTH * deformer.directionAndEndRadius.xyz;
    float startRadius = deformer.originAndStartRadius.w;
    float endRadius = deformer.directionAndEndRadius.w;

    vec3 axis = end - start;
    float axisLengthSquared = dot(axis, axis);
    float radiusDelta = startRadius - endRadius;
    float taper = axisLengthSquared - radiusDelta * radiusDelta;
    float inverseLengthSquared = 1.0 / axisLengthSquared;

    vec3 toPoint = position - start;
    float along = dot(toPoint, axis);
    float beyond = along - axisLengthSquared;
    vec3 perpendicular = toPoint * axisLengthSquared - axis * along;
    float perpendicularSquared = dot(perpendicular, perpendicular);
    float alongSquared = along * along * axisLengthSquared;
    float beyondSquared = beyond * beyond * axisLengthSquared;

    float threshold = sign(radiusDelta) * radiusDelta * radiusDelta * perpendicularSquared;
    if (sign(beyond) * taper * beyondSquared > threshold) {
        return sqrt(perpendicularSquared + beyondSquared) * inverseLengthSquared - endRadius;
    }
    if (sign(along) * taper * alongSquared < threshold) {
        return sqrt(perpendicularSquared + alongSquared) * inverseLengthSquared - startRadius;
    }
    return (sqrt(perpendicularSquared * taper * inverseLengthSquared) + along * radiusDelta)
            * inverseLengthSquared - startRadius;
}

float deformerFactor(vec3 position, float noise) {
    float nearest = 1.0;
    for (int index = 0; index < DEFORMER_COUNT; ++index) {
        float distanceToCone = roundedConeDistance(deformers[index], position);
        float softened = smoothstep(0.75, 1.0, min(1.0, distanceToCone + noise * 0.8));
        nearest = min(nearest, softened);
    }
    return clamp(nearest, 0.0, 1.0);
}

float densityAt(vec3 worldPosition) {
    float voxelValue = trilinearDensity(worldPosition);
    float noise = sampleNoise(worldPosition);
    vec3 fromSeed = worldPosition - seedPoint.xyz;
    vec3 radius = max(growthRadius.xyz - 0.1, vec3(0.0001));
    float radialDistance = min(1.0, length(fromSeed / radius));
    float voxelDistance = min(1.0, 1.0 - voxelValue / max(1.0, PROPAGATION_DISTANCE));
    float shaped = smoothstep(1.0 - DENSITY_FALLOFF, 1.0, max(radialDistance, voxelDistance));
    return clamp(clamp(voxelValue, 0.0, 1.0) * (1.0 - min(1.0, shaped + noise)), 0.0, 1.0);
}

float henyeyGreenstein(float g, float cosTheta) {
    float denominator = 1.0 + g * g - 2.0 * g * cosTheta;
    return (1.0 / (4.0 * PI)) * ((1.0 - g * g) / (denominator * sqrt(max(0.0001, denominator))));
}

float mie(float g, float cosTheta) {
    float denominator = 1.0 + g * g - 2.0 * g * cosTheta;
    return (3.0 / (8.0 * PI)) * (((1.0 - g * g) * (1.0 + cosTheta * cosTheta))
            / ((2.0 + g * g) * (denominator * sqrt(max(0.0001, denominator)))));
}

float phase(float cosTheta) {
    if (PHASE_FUNCTION == 1) {
        return mie(ANISOTROPY, cosTheta);
    }
    if (PHASE_FUNCTION == 2) {
        return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);
    }
    return henyeyGreenstein(ANISOTROPY, cosTheta);
}

vec3 worldFromDepth(vec2 uv, float deviceDepth) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, deviceDepth * 2.0 - 1.0, 1.0);
    vec4 worldPosition = cameraInverseViewProjection * clipPosition;
    return worldPosition.xyz / worldPosition.w;
}

vec3 rayDirectionFor(vec2 uv) {
    vec3 viewDirection = (cameraInverseProjection * vec4(uv * 2.0 - 1.0, 0.0, 1.0)).xyz;
    return normalize((cameraToWorld * vec4(viewDirection, 0.0)).xyz);
}

float lightMarch(vec3 origin, float startDensity, vec3 towardsLight) {
    float opticalDepth = 0.0;
    vec3 position = origin;
    float sampled = startDensity;
    for (int step = 0; step < LIGHT_STEP_COUNT; ++step) {
        opticalDepth += sampled * SHADOW_DENSITY;
        position += LIGHT_STEP_SIZE * towardsLight;
        sampled = densityAt(position);
    }
    return opticalDepth;
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    if (pixel.x >= BUFFER_WIDTH || pixel.y >= BUFFER_HEIGHT) {
        return;
    }
    vec2 uv = (vec2(pixel) + 0.5) / vec2(BUFFER_WIDTH, BUFFER_HEIGHT);

    vec3 origin = cameraWorldPosition.xyz;
    vec3 rayDirection = rayDirectionFor(uv);
    float depthSample = texture(sceneDepth, uv).r;
    vec3 scenePosition = worldFromDepth(uv, depthSample);
    float sceneDistance = dot(scenePosition - origin, rayDirection);

    vec3 accumulatedColor = vec3(0.0);
    float alpha = 1.0;
    float travelled = 0.0;
    int occupied = voxelAt(origin);

    while (occupied == 0 && travelled < MAX_DISTANCE) {
        travelled += COARSE_STEP;
        occupied = voxelAt(origin + travelled * rayDirection);
    }

    if (occupied != 0) {
        travelled = max(0.0, travelled - COARSE_STEP);
        accumulatedColor = albedo.rgb;
        float extinction = ABSORPTION + SCATTERING;
        vec3 towardsLight = normalize(-sunDirection.xyz);
        float phaseValue = phase(dot(rayDirection, towardsLight));
        float thickness = 0.0;
        float accumulatedDensity = 0.0;

        for (int step = 0; step < STEP_COUNT && travelled < sceneDistance; ++step) {
            vec3 samplePosition = origin + travelled * rayDirection;
            float noise = sampleNoise(samplePosition);
            float sampled = densityAt(samplePosition) * deformerFactor(samplePosition, noise);
            accumulatedDensity += sampled * VOLUME_DENSITY;
            travelled += STEP_SIZE;
            thickness += STEP_SIZE * sampled;
            alpha = exp(-thickness * accumulatedDensity * extinction);
            if (sampled > 0.001) {
                float opticalDepth = lightMarch(samplePosition, sampled, towardsLight);
                vec3 attenuation = exp(-(vec3(opticalDepth) / max(extinctionColor.rgb, vec3(0.0001)))
                        * extinction * SHADOW_DENSITY);
                accumulatedColor += lightColor.rgb * attenuation * alpha * phaseValue
                        * SCATTERING * VOLUME_DENSITY * sampled;
            }
            if (alpha < ALPHA_THRESHOLD) {
                break;
            }
        }

        if (alpha < ALPHA_THRESHOLD) {
            alpha = 0.0;
        }
    }

    imageStore(scatteredColor, pixel, vec4(clamp(accumulatedColor, 0.0, 64.0), 1.0));
    imageStore(transmittance, pixel, vec4(clamp(alpha, 0.0, 1.0), 0.0, 0.0, 0.0));
}
