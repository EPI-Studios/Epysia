package fr.epistudio.epysia.ui;

public record UiColor(float red, float green, float blue, float alpha) {

    public static final UiColor WHITE = new UiColor(1.0f, 1.0f, 1.0f, 1.0f);
    public static final UiColor BLACK = new UiColor(0.0f, 0.0f, 0.0f, 1.0f);
    public static final UiColor TRANSPARENT = new UiColor(0.0f, 0.0f, 0.0f, 0.0f);

    public static UiColor rgb(float red, float green, float blue) {
        return new UiColor(red, green, blue, 1.0f);
    }

    public static UiColor rgba(float red, float green, float blue, float alpha) {
        return new UiColor(red, green, blue, alpha);
    }

    public UiColor withAlpha(float newAlpha) {
        return new UiColor(red, green, blue, newAlpha);
    }
}
