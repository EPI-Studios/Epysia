vec3 graphPreviewChecker(vec2 previewUv) {
    vec2 cell = floor(previewUv * 8.0);
    float parity = mod(cell.x + cell.y, 2.0);
    return mix(vec3(0.32, 0.32, 0.34), vec3(0.52, 0.52, 0.55), parity);
}
