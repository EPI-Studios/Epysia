package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.text.Font;

public final class UiLabel extends UiNode {

    private String text = "";
    private UiColor color = UiColor.WHITE;
    private Font font;

    public UiLabel setText(String text) {
        this.text = text;
        return this;
    }

    public UiLabel setColor(UiColor color) {
        this.color = color;
        return this;
    }

    public UiLabel setFont(Font font) {
        this.font = font;
        return this;
    }

    public String text() {
        return text;
    }

    public UiColor color() {
        return color;
    }

    public Font font() {
        return font;
    }
}
