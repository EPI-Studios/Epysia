package fr.epistudio.epysia.ui;

public enum UiAutomaticSize {
    NONE,
    X,
    Y,
    XY;

    public boolean growsWidth() {
        return this == X || this == XY;
    }

    public boolean growsHeight() {
        return this == Y || this == XY;
    }
}
