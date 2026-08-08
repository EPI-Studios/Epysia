package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

import java.util.function.Consumer;

@EpysiaComponent(name = "Ui Check Box", category = "UI")
public final class UiCheckBox extends UiElement {
    private static final float CHECK_INSET = 0.25f;

    @Export(label = "Checked")
    private boolean checked;
    @Export(label = "Box", color = true)
    private final Vector4f boxColor = new Vector4f(0.16f, 0.17f, 0.21f, 1.0f);
    @Export(label = "Check", color = true)
    private final Vector4f checkColor = new Vector4f(0.35f, 0.7f, 1.0f, 1.0f);

    private Consumer<Boolean> onChanged = value -> {
    };

    public boolean checked() {
        return checked;
    }

    public UiCheckBox setChecked(boolean value) {
        this.checked = value;
        return this;
    }

    public UiCheckBox setOnChanged(Consumer<Boolean> listener) {
        this.onChanged = listener;
        return this;
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    protected void paint(UiPainter painter) {
        UiRect rect = computedRect();
        painter.fillRect(rect, UiColors.of(boxColor));
        if (!checked) {
            return;
        }
        float insetX = rect.width() * CHECK_INSET;
        float insetY = rect.height() * CHECK_INSET;
        painter.fillRect(new UiRect(rect.x() + insetX, rect.y() + insetY,
                rect.width() - insetX * 2.0f, rect.height() - insetY * 2.0f), UiColors.of(checkColor));
    }

    @Override
    protected void onPointerUp(float localX, float localY, boolean inside) {
        if (!inside) {
            return;
        }
        checked = !checked;
        onChanged.accept(checked);
    }
}
