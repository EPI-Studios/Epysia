layout(binding = 11) uniform sampler2D opaqueSceneDepth;

in float particleViewDepth;

float sceneViewDepthAtFragment() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(opaqueSceneDepth, 0));
    float deviceDepth = texture(opaqueSceneDepth, clamp(uv, vec2(0.0), vec2(1.0))).r;
    float near = max(frame.clusterParams.x, 1.0e-4);
    float far = max(frame.clusterParams.y, near + 1.0e-3);
    float normalized = deviceDepth * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - normalized * (far - near));
}

float particleDepthFade() {
    float fadeDistance = particleDepthFadeDistance();
    if (fadeDistance <= 0.0) {
        return 1.0;
    }
    float behindParticle = sceneViewDepthAtFragment() - particleViewDepth;
    return clamp(behindParticle / fadeDistance, 0.0, 1.0);
}
