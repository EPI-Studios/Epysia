#version 430 core
#include "environment/capture_common.glsl"

layout(binding = 1) uniform samplerCube environmentMap;

in vec2 vertexUv;
out vec4 outColor;

const float PI = 3.14159265359;

void main() {
    vec3 normal = captureDirection(vertexUv);
    vec3 upAxis = abs(normal.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 right = normalize(cross(upAxis, normal));
    vec3 up = cross(normal, right);

    vec3 irradiance = vec3(0.0);
    float sampleCount = 0.0;
    for (float phi = 0.0; phi < 2.0 * PI; phi += 0.15) {
        for (float theta = 0.0; theta < 0.5 * PI; theta += 0.075) {
            vec3 tangentSample = vec3(sin(theta) * cos(phi), sin(theta) * sin(phi), cos(theta));
            vec3 sampleDirection = tangentSample.x * right + tangentSample.y * up + tangentSample.z * normal;
            irradiance += texture(environmentMap, sampleDirection).rgb * cos(theta) * sin(theta);
            sampleCount += 1.0;
        }
    }
    irradiance = PI * irradiance / sampleCount;
    outColor = vec4(irradiance, 1.0);
}
