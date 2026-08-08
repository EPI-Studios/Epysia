const float PI = 3.14159265358979;

layout(std140, binding = 0) uniform VolumeUniforms {
    mat4 volumeWorldToLocal;
    mat4 volumeLocalToWorld;
    mat4 cameraInverseProjection;
    mat4 cameraToWorld;
    mat4 cameraInverseViewProjection;
    vec4 volumeExtents;
    vec4 voxelResolution;
    vec4 growthRadius;
    vec4 seedPoint;
    vec4 cameraWorldPosition;
    vec4 sunDirection;
    vec4 albedo;
    vec4 lightColor;
    vec4 extinctionColor;
    vec4 animationDirection;
    vec4 marchParameters;
    vec4 mediaParameters;
    vec4 falloffParameters;
    vec4 bufferParameters;
    vec4 frameParameters;
    vec4 deformerParameters;
};

#define VOXEL_SIZE marchParameters.x
#define STEP_SIZE marchParameters.y
#define LIGHT_STEP_SIZE marchParameters.z
#define DETAIL_SCALE marchParameters.w

#define ABSORPTION mediaParameters.x
#define SCATTERING mediaParameters.y
#define VOLUME_DENSITY mediaParameters.z
#define SHADOW_DENSITY mediaParameters.w

#define DENSITY_FALLOFF falloffParameters.x
#define ALPHA_THRESHOLD falloffParameters.y
#define ANISOTROPY falloffParameters.z
#define PROPAGATION_DISTANCE falloffParameters.w

#define BUFFER_WIDTH int(bufferParameters.x)
#define BUFFER_HEIGHT int(bufferParameters.y)
#define STEP_COUNT int(bufferParameters.z)
#define LIGHT_STEP_COUNT int(bufferParameters.w)

#define FRAME_TIME frameParameters.x
#define PHASE_FUNCTION int(frameParameters.y)
#define VOXEL_COUNT int(frameParameters.z)
#define SHAPE_COUNT int(frameParameters.w)

#define DEFORMER_COUNT int(deformerParameters.x)
#define DEFORMER_DEPTH deformerParameters.y

uint toVoxelIndex(uvec3 position) {
    uvec3 resolution = uvec3(voxelResolution.xyz);
    return position.x + position.y * resolution.x + position.z * resolution.x * resolution.y;
}

uvec3 toVoxelPosition(uint index) {
    uvec3 resolution = uvec3(voxelResolution.xyz);
    uint x = index % resolution.x;
    uint y = (index / resolution.x) % resolution.y;
    uint z = index / (resolution.x * resolution.y);
    return uvec3(x, y, z);
}

vec3 voxelCenterLocal(uvec3 position) {
    return (vec3(position) + 0.5) * VOXEL_SIZE - volumeExtents.xyz;
}

vec3 worldToVoxelSpace(vec3 worldPosition) {
    vec3 local = (volumeWorldToLocal * vec4(worldPosition, 1.0)).xyz;
    return (local + volumeExtents.xyz) / VOXEL_SIZE;
}

bool insideVolume(vec3 worldPosition) {
    vec3 local = (volumeWorldToLocal * vec4(worldPosition, 1.0)).xyz;
    return all(lessThanEqual(abs(local), volumeExtents.xyz));
}
