package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

import java.util.function.Consumer;

@EpysiaComponent(name = "Ui Slider", category = "UI")
public final class UiSlider extends UiElement {
    private static final float TRACK_HEIGHT_RATIO = 0.35f;
    private static final float KNOB_WIDTH = 10.0f;

    @Export(label = "Minimum", step = 0.1f)
    private float minimum;
    @Export(label = "Maximum", step = 0.1f)
    private float maximum = 1.0f;
    @Export(label = "Value", step = 0.1f)
    private float value;
    @Export(label = "Track", color = true)
    private final Vector4f trackColor = new Vector4f(0.14f, 0.15f, 0.18f, 1.0f);
    @Export(label = "Fill", color = true)
    private final Vector4f fillColor = new Vector4f(0.35f, 0.7f, 1.0f, 1.0f);
    @Export(label = "Knob", color = true)
    private final Vector4f knobColor = new Vector4f(0.9f, 0.92f, 0.96f, 1.0f);

    private Consumer<Float> onChanged = amount -> {
    };

    public float value() {
        return value;
    }

    public UiSlider setRange(float newMinimum, float newMaximum) {
        this.minimum = newMinimum;
        this.maximum = Math.max(newMinimum + 1.0e-4f, newMaximum);
        return this;
    }

    public UiSlider setValue(float newValue) {
        this.value = Math.clamp(newValue, minimum, maximum);
        return this;
    }

    public UiSlider setOnChanged(Consumer<Float> listener) {
        this.onChanged = listener;
        return this;
    }

    public float ratio() {
        return (value - minimum) / Math.max(1.0e-4f, maximum - minimum);
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    protected void paint(UiPainter painter) {
        UiRect rect = computedRect();
        float trackHeight = rect.height() * TRACK_HEIGHT_RATIO;
        float trackY = rect.y() + (rect.height() - trackHeight) * 0.5f;
        painter.fillRect(new UiRect(rect.x(), trackY, rect.width(), trackHeight), UiColors.of(trackColor));
        painter.fillRect(new UiRect(rect.x(), trackY, rect.width() * ratio(), trackHeight),
                UiColors.of(fillColor));
        painter.fillRect(new UiRect(rect.x() + rect.width() * ratio() - KNOB_WIDTH * 0.5f, rect.y(),
                KNOB_WIDTH, rect.height()), UiColors.of(knobColor));
    }

    @Override
    protected void onPointerDown(float localX, float localY) {
        applyPointer(localX);
    }

    @Override
    protected void onPointerDrag(float localX, float localY) {
        applyPointer(localX);
    }

    private void applyPointer(float localX) {
        float width = Math.max(1.0e-4f, computedRect().width());
        float updated = minimum + Math.clamp(localX / width, 0.0f, 1.0f) * (maximum - minimum);
        if (updated == value) {
            return;
        }
        value = updated;
        onChanged.accept(value);
    }
}
