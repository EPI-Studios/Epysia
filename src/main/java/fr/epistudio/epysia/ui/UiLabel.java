package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Label", category = "UI",
        description = "Draws a line of text with the element's font and color.")
public final class UiLabel extends UiElement {
    @Export(label = "Text")
    private String text = "Label";
    @Export(label = "Color", color = true)
    private final Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    @Export(label = "Font", assetExtensions = {".ttf", ".otf"})
    private String fontPath = "";
    @Export(label = "Font size", min = 1.0f, max = 256.0f, step = 1.0f)
    private float fontSize = UiFontStyle.DEFAULT_SIZE;
    @Export(label = "Align")
    private UiTextAlignment horizontalAlignment = UiTextAlignment.LEFT;
    @Export(label = "Vertical align")
    private UiVerticalAlignment verticalAlignment = UiVerticalAlignment.TOP;
    @Export(label = "Wrap")
    private UiAutowrap autowrap = UiAutowrap.OFF;

    public String text() {
        return text;
    }

    public UiLabel setText(String value) {
        this.text = value == null ? "" : value;
        return this;
    }

    public UiLabel setColor(UiColor value) {
        UiColors.copyInto(value, color);
        return this;
    }

    public UiLabel setFontPath(String path) {
        this.fontPath = path == null ? "" : path;
        return this;
    }

    public UiLabel setFontSize(float value) {
        this.fontSize = value;
        return this;
    }

    public UiLabel setHorizontalAlignment(UiTextAlignment alignment) {
        this.horizontalAlignment = alignment;
        return this;
    }

    public UiLabel setVerticalAlignment(UiVerticalAlignment alignment) {
        this.verticalAlignment = alignment;
        return this;
    }

    public UiLabel setAutowrap(UiAutowrap mode) {
        this.autowrap = mode;
        return this;
    }

    public UiFontStyle fontStyle() {
        return UiFontStyle.of(fontPath, fontSize);
    }

    @Override
    protected void paint(UiPainter painter) {
        if (text.isEmpty()) {
            return;
        }
        UiTextLayout.draw(painter, computedRect(), text, fontStyle(), UiColors.of(color),
                horizontalAlignment, verticalAlignment, autowrap);
    }
}
