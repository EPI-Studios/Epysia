package fr.epistudio.epysia.ui;

public enum UiStackAlignment {
    START(0.0f),
    CENTER(0.5f),
    END(1.0f);

    private final float relativePosition;

    UiStackAlignment(float relativePosition) {
        this.relativePosition = relativePosition;
    }

    public float relativePosition() {
        return relativePosition;
    }
}
