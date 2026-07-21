uniform float tintAmount; // @default 0.0

void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time) {
    albedoColor = vec4(tintAmount, 0.0, 1.0 - tintAmount, 1.0);
    emissive = albedoColor.rgb * 2.0;
}
