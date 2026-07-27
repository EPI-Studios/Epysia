layout(binding = 1) uniform sampler2D skyTexture;

const float SKY_EQUIRECT_PI = 3.14159265359;

vec3 skyRadiance(vec3 direction, vec3 sunDirection, float intensity) {
    vec3 unit = normalize(direction);
    vec2 uv = vec2(atan(unit.z, unit.x) / (2.0 * SKY_EQUIRECT_PI) + 0.5,
                   acos(clamp(unit.y, -1.0, 1.0)) / SKY_EQUIRECT_PI);
    return texture(skyTexture, uv).rgb * intensity;
}
