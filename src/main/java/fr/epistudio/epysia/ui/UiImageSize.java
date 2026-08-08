package fr.epistudio.epysia.ui;

public record UiImageSize(float width, float height) {
    private static final UiImageSize UNKNOWN = new UiImageSize(0.0f, 0.0f);

    public static UiImageSize unknown() {
        return UNKNOWN;
    }

    public boolean known() {
        return width > 0.0f && height > 0.0f;
    }
}
