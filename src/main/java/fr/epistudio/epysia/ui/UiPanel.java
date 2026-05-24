package fr.epistudio.epysia.ui;

public final class UiPanel extends UiNode {

    private UiColor color = UiColor.rgba(0.1f, 0.1f, 0.12f, 0.85f);

    public UiPanel setColor(UiColor color) {
        this.color = color;
        return this;
    }

    public UiColor color() {
        return color;
    }
}
