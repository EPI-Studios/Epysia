float graphDitherThreshold(vec2 coordinate) {
    ivec2 cell = ivec2(floor(coordinate));
    int folded = cell.x ^ cell.y;
    int index = ((folded & 1) << 3) | ((cell.y & 1) << 2)
            | (((folded >> 1) & 1) << 1) | ((cell.y >> 1) & 1);
    return float(index) / 16.0;
}
