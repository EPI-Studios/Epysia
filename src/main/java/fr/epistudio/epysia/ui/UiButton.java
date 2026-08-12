package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

import java.util.function.Consumer;

@EpysiaComponent(name = "Ui Button", category = "UI")
public final class UiButton extends UiElement {
    @Export(label = "Idle", color = true)
    private final Vector4f idleColor = new Vector4f(0.18f, 0.18f, 0.22f, 0.95f);
    @Export(label = "Hover", color = true)
    private final Vector4f hoverColor = new Vector4f(0.28f, 0.28f, 0.34f, 0.95f);
    @Export(label = "Pressed", color = true)
    private final Vector4f pressedColor = new Vector4f(0.4f, 0.4f, 0.5f, 0.95f);

    private boolean hovered;
    private boolean pressed;
    private Runnable onClick = () -> {
    };

    public UiButton setColors(UiColor idle, UiColor hover, UiColor pressedColour) {
        UiColors.copyInto(idle, idleColor);
        UiColors.copyInto(hover, hoverColor);
        UiColors.copyInto(pressedColour, this.pressedColor);
        return this;
    }

    public UiButton setOnClick(Runnable listener) {
        this.onClick = listener == null ? () -> {
        } : listener;
        return this;
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean pressed() {
        return pressed;
    }

    public void setHovered(boolean value) {
        this.hovered = value;
    }

    public void setPressed(boolean value) {
        this.pressed = value;
    }

    public UiColor currentColor() {
        if (pressed) {
            return UiColors.of(pressedColor);
        }
        return hovered ? UiColors.of(hoverColor) : UiColors.of(idleColor);
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    protected void paint(UiPainter painter) {
        painter.fillRect(computedRect(), currentColor());
    }

    @Override
    protected void onHoverChanged(boolean value) {
        hovered = value;
    }

    @Override
    protected void onPointerDown(float localX, float localY) {
        pressed = true;
    }

    @Override
    protected void onPointerUp(float localX, float localY, boolean inside) {
        pressed = false;
        if (inside) {
            onClick.run();
        }
    }
}
