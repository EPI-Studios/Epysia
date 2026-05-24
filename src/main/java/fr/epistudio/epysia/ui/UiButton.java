package fr.epistudio.epysia.ui;

public final class UiButton extends UiNode {

    private UiColor idleColor = UiColor.rgba(0.18f, 0.18f, 0.22f, 0.95f);
    private UiColor hoverColor = UiColor.rgba(0.28f, 0.28f, 0.34f, 0.95f);
    private UiColor pressedColor = UiColor.rgba(0.4f, 0.4f, 0.5f, 0.95f);
    private boolean hovered;
    private boolean pressed;
    private Runnable onClick = () -> {};

    public UiButton setIdleColor(UiColor color) {
        this.idleColor = color;
        return this;
    }

    public UiButton setHoverColor(UiColor color) {
        this.hoverColor = color;
        return this;
    }

    public UiButton setPressedColor(UiColor color) {
        this.pressedColor = color;
        return this;
    }

    public UiButton setOnClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public UiColor currentColor() {
        if (pressed) {
            return pressedColor;
        }
        if (hovered) {
            return hoverColor;
        }
        return idleColor;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean pressed() {
        return pressed;
    }

    public void fireClick() {
        onClick.run();
    }
}
