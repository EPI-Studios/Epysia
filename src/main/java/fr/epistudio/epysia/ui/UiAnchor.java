package fr.epistudio.epysia.ui;

public enum UiAnchor {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    MIDDLE_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    MIDDLE_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float relativeX;
    private final float relativeY;

    UiAnchor(float relativeX, float relativeY) {
        this.relativeX = relativeX;
        this.relativeY = relativeY;
    }

    public float relativeX() {
        return relativeX;
    }

    public float relativeY() {
        return relativeY;
    }
}
