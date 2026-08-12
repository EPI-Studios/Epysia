package fr.epistudio.epysia.ui;

public record UiFontStyle(String path, float size, float bakeSize, float edgeCutoff) {
    public static final float DEFAULT_SIZE = 24.0f;
    public static final float DEFAULT_EDGE_CUTOFF = 0.5f;

    public static UiFontStyle of(String path, float size) {
        float resolvedSize = size <= 0.0f ? DEFAULT_SIZE : size;
        return new UiFontStyle(path == null ? "" : path, resolvedSize, resolvedSize, 0.0f);
    }

    public static UiFontStyle pixel(String path, float size, float bakeSize) {
        return pixel(path, size, bakeSize, DEFAULT_EDGE_CUTOFF);
    }

    public static UiFontStyle pixel(String path, float size, float bakeSize, float edgeCutoff) {
        UiFontStyle smooth = of(path, size);
        if (bakeSize <= 0.0f || bakeSize >= smooth.size()) {
            return smooth;
        }
        return new UiFontStyle(smooth.path(), smooth.size(), bakeSize, Math.clamp(edgeCutoff, 0.0f, 1.0f));
    }

    public boolean usesDefaultFont() {
        return path.isEmpty();
    }

    public boolean magnified() {
        return bakeSize < size;
    }

    public float magnification() {
        return size / bakeSize;
    }
}
