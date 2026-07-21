// Surface shader: injected into the standard PBR lit pipeline.
// Both functions are optional; delete the ones you do not need.
//
// void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, in vec2 uv, in float time)
//     Runs in every vertex stage (lit and shadow passes), so displacement such as
//     wind sway also moves the cast shadow. No samplers are available here.
// void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time)
//     Runs in the lit fragment stage after texture sampling, albedoColor.a feeds the
//     alpha cutoff. The material samplers (albedo, normalMap, metallicRoughnessMap,
//     occlusionMap, emissiveMap) can be sampled here, keep parameter names distinct
//     from the sampler names.
//
// Declare your own uniforms at file scope. Each material gets its own values, they
// show up in the inspector and are settable from scripts with material.setFloat(...):
//     float, int, bool, vec2, vec3, vec4, mat4, sampler2D
// Append "// @color" after a vec3/vec4 uniform to get a color picker.
// Append "// @default 1.0, 0.5, 0.0" to give a uniform a starting value.
//
// Object transform helpers, available in both functions:
//     objectToWorld()  - the model matrix
//     objectOrigin()   - the object position in world space
//     objectScale()    - the object scale
// Reading them inside surfaceColor disables GPU instancing for that material.
//
// Code outside these functions is shared by all stages and must stay stage-neutral
// (no samplers, no stage-specific inputs).

// uniform float swayStrength; // @default 0.12
// uniform vec3 tintColor;     // @color @default 1.0, 1.0, 1.0

void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, in vec2 uv, in float time) {
    // Wind sway example (uncomment to try, together with the swayStrength uniform above):
    // float heightWeight = clamp(localPosition.y, 0.0, 1.0);
    // float phase = dot(objectOrigin().xz, vec2(0.37, 0.61));
    // float slowWave = sin(time * 1.3 + phase);
    // float fastWave = sin(time * 4.1 + phase * 2.0) * 0.3;
    // worldPosition.xz += heightWeight * swayStrength * vec2(slowWave + fastWave, slowWave * 0.6);
}

void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time) {
}
