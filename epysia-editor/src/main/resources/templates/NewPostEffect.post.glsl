// Post effect: runs as a fullscreen pass in the camera post-processing stack.
// Entry point (required):
//     vec4 postEffect(vec4 sceneColor, vec2 uv)
//
// Scene helpers, no depth maths required:
//     sceneIsSky(uv)           - true where nothing was drawn
//     sceneViewDepth(uv)       - linear distance along the camera axis, in world units
//     sceneCameraDistance(uv)  - straight-line distance from the camera, in world units
//     sceneWorldPosition(uv)   - world position of the pixel
//     sceneRawDepth(uv)        - the raw non-linear depth buffer value
// Frame values:
//     time, resolution, cameraPosition, nearPlane, farPlane, inverseViewProjection
//
// Declare your own uniforms at file scope; they show up in the editor and
// are settable from scripts via services.postEffects():
//     float, int, bool, vec2, vec3, vec4, mat4, sampler2D
//     float and vec4 arrays (script controlled)
// Append "// @color" after a vec3/vec4 uniform to get a color picker.
// Append "// @default 1.0, 0.5, 0.0" to give a uniform a starting value.
//
// Add "// @insertion before_tonemap" or "// @insertion after_tonemap" to pin where
// this effect runs. Work in HDR before tonemapping (fog, glow), in LDR after it
// (colour grading, dithering, retro looks). Without the annotation the insertion
// point is chosen per stack entry in the editor.

uniform float intensity; // @default 0.0

// Film grain example (uncomment to try, then raise intensity above zero):
// float grainNoise(vec2 seed) {
//     return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
// }

vec4 postEffect(vec4 sceneColor, vec2 uv) {
    // float grain = grainNoise(uv * resolution + vec2(time)) - 0.5;
    // sceneColor.rgb += grain * intensity;
    return sceneColor;
}
