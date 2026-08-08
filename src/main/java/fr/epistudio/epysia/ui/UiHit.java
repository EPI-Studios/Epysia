package fr.epistudio.epysia.ui;

public record UiHit(UiElement element, float localX, float localY) {
    private static final UiHit NONE = new UiHit(null, 0.0f, 0.0f);

    public static UiHit none() {
        return NONE;
    }
}
