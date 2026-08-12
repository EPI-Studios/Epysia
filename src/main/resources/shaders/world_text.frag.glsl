#version 430 core

in vec2 textTexCoord;
in vec4 textColour;
in float textOutline;

layout(binding = 1) uniform sampler2D atlasTexture;

out vec4 outColour;

float haloCoverage(vec2 texel) {
    float halo = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            halo = max(halo, texture(atlasTexture, textTexCoord + vec2(x, y) * texel).a);
        }
    }
    return halo;
}

void main() {
    float coverage = texture(atlasTexture, textTexCoord).a;
    vec2 texel = 1.0 / vec2(textureSize(atlasTexture, 0));
    float halo = textOutline > 0.0 ? haloCoverage(texel) * textOutline : 0.0;
    float alpha = max(coverage, halo) * textColour.a;
    if (alpha <= 0.003) {
        discard;
    }
    outColour = vec4(textColour.rgb * coverage, alpha);
}
