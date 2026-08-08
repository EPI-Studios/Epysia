layout(std140, binding = 5) uniform Object2dUbo {
    vec4 basis;
    vec4 translation;
} object2d;

vec2 objectToWorld2d(vec2 localPosition) {
    return vec2(object2d.basis.x * localPosition.x + object2d.basis.z * localPosition.y + object2d.translation.x,
                object2d.basis.y * localPosition.x + object2d.basis.w * localPosition.y + object2d.translation.y);
}
