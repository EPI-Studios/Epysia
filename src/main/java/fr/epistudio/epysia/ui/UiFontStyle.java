package fr.epistudio.epysia.ui;

public record UiFontStyle(String path, float size) {
    public static final float DEFAULT_SIZE = 24.0f;

    public static UiFontStyle of(String path, float size) {
        return new UiFontStyle(path == null ? "" : path, size <= 0.0f ? DEFAULT_SIZE : size);
    }

    public boolean usesDefaultFont() {
        return path.isEmpty();
    }
}
