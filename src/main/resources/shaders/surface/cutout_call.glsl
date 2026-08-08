vec4 surfaceAlbedo = texture(albedo, vertexUv);
    float surfaceMetallic = 0.0;
    float surfaceRoughness = 1.0;
    vec3 surfaceEmissive = vec3(0.0);
    surfacePrepare(vertexUv, vertexWorldPosition, frameTime());
    surfaceColor(surfaceAlbedo, surfaceMetallic, surfaceRoughness, surfaceEmissive, vertexUv, vertexWorldPosition, frameTime());
    surfaceShade(surfaceAlbedo, surfaceMetallic, surfaceRoughness, surfaceEmissive,
                 vec3(0.0, 1.0, 0.0), normalize(frame.cameraPosition.xyz - vertexWorldPosition),
                 vertexUv, vertexWorldPosition, frameTime());
    if (surfaceAlbedo.a < material.alphaCutoff) {
        discard;
    }
    return;
