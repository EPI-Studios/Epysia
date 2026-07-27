vec3 surfaceWorldPosition = worldPosition.xyz;
    surfaceVertex(surfaceWorldPosition, inPosition, normalize(mat3(OBJECT_NORMAL_MATRIX) * inNormal), inUv, frameTime());
    worldPosition = vec4(surfaceWorldPosition, 1.0);
