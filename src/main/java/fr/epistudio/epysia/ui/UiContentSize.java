package fr.epistudio.epysia.ui;

public record UiContentSize(float width, float height) {

    public static final UiContentSize NONE = new UiContentSize(0.0f, 0.0f);
}
