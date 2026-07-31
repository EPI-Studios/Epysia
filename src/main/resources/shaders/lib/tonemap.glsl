vec3 acesFilmTonemap(vec3 color) {
    const mat3 inputMatrix = mat3(
        0.59719, 0.07600, 0.02840,
        0.35458, 0.90834, 0.13383,
        0.04823, 0.01566, 0.83777
    );
    const mat3 outputMatrix = mat3(
        1.60475, -0.10208, -0.00327,
        -0.53108, 1.10813, -0.07276,
        -0.07367, -0.00605, 1.07602
    );
    vec3 aces = inputMatrix * color;
    vec3 rttOdt = (aces * (aces + 0.0245786) - 0.000090537)
            / (aces * (0.983729 * aces + 0.4329510) + 0.238081);
    return clamp(outputMatrix * rttOdt, 0.0, 1.0);
}

vec3 linearToSrgb(vec3 color) {
    return pow(color, vec3(1.0 / 2.2));
}

vec3 srgbToLinear(vec3 color) {
    return pow(color, vec3(2.2));
}

vec3 acesFilmTonemapInverse(vec3 color) {
    const mat3 inputMatrix = mat3(
        1.60475, -0.10208, -0.00327,
        -0.53108, 1.10813, -0.07276,
        -0.07367, -0.00605, 1.07602
    );
    const mat3 outputMatrix = mat3(
        0.59719, 0.07600, 0.02840,
        0.35458, 0.90834, 0.13383,
        0.04823, 0.01566, 0.83777
    );
    vec3 rttOdt = inputMatrix * clamp(color, 0.0, 0.9999);
    vec3 quadraticA = 0.983729 * rttOdt - 1.0;
    vec3 quadraticB = 0.4329510 * rttOdt - 0.0245786;
    vec3 quadraticC = 0.238081 * rttOdt + 0.000090537;
    vec3 discriminant = max(quadraticB * quadraticB - 4.0 * quadraticA * quadraticC, 0.0);
    vec3 aces = (-quadraticB - sqrt(discriminant)) / (2.0 * quadraticA);
    return max(outputMatrix * aces, 0.0);
}
