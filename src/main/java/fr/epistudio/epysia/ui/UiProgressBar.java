package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Progress Bar", category = "UI")
public final class UiProgressBar extends UiElement {
    @Export(label = "Value", min = 0.0f, max = 1.0f, step = 0.01f)
    private float value;
    @Export(label = "Background", color = true)
    private final Vector4f backgroundColor = new Vector4f(0.12f, 0.13f, 0.16f, 1.0f);
    @Export(label = "Fill", color = true)
    private final Vector4f fillColor = new Vector4f(0.35f, 0.75f, 0.45f, 1.0f);

    public float value() {
        return value;
    }

    public UiProgressBar setValue(float amount) {
        this.value = Math.clamp(amount, 0.0f, 1.0f);
        return this;
    }

    @Override
    protected void paint(UiPainter painter) {
        UiRect rect = computedRect();
        painter.fillRect(rect, UiColors.of(backgroundColor));
        painter.fillRect(new UiRect(rect.x(), rect.y(), rect.width() * value, rect.height()),
                UiColors.of(fillColor));
    }
}
