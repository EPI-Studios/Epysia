package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Panel", category = "UI")
public final class UiPanel extends UiElement {
    @Export(label = "Color", color = true)
    private final Vector4f color = new Vector4f(0.1f, 0.1f, 0.12f, 0.85f);

    public UiColor color() {
        return UiColors.of(color);
    }

    public UiPanel setColor(UiColor value) {
        UiColors.copyInto(value, color);
        return this;
    }

    @Override
    protected void paint(UiPainter painter) {
        painter.fillRect(computedRect(), color());
    }
}
