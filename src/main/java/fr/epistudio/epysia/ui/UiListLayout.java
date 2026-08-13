package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

import java.util.List;

@EpysiaComponent(name = "Ui List Layout", category = "UI")
public final class UiListLayout extends UiLayout {

    @Export(label = "Direction")
    private UiListDirection direction = UiListDirection.VERTICAL;

    @Export(label = "Spacing", step = 1.0f)
    private float spacing = 4.0f;

    @Export(label = "Cross alignment")
    private UiListAlignment crossAlignment = UiListAlignment.START;

    public UiListDirection direction() {
        return direction;
    }

    public UiListLayout setDirection(UiListDirection value) {
        direction = value == null ? UiListDirection.VERTICAL : value;
        return this;
    }

    public float spacing() {
        return spacing;
    }

    public UiListLayout setSpacing(float value) {
        spacing = Math.max(0.0f, value);
        return this;
    }

    public UiListAlignment crossAlignment() {
        return crossAlignment;
    }

    public UiListLayout setCrossAlignment(UiListAlignment value) {
        crossAlignment = value == null ? UiListAlignment.START : value;
        return this;
    }

    @Override
    public void arrange(UiRect containerRect, List<UiElement> children) {
        UiRect area = inner(containerRect);
        float cursor = direction == UiListDirection.VERTICAL ? area.y() : area.x();
        for (UiElement child : children) {
            if (!child.visible()) {
                continue;
            }
            UiRect placed = place(child, area, cursor);
            child.placeAt(placed);
            cursor += (direction == UiListDirection.VERTICAL ? placed.height() : placed.width()) + spacing;
        }
    }

    private UiRect place(UiElement child, UiRect area, float cursor) {
        UiRect current = child.computedRect();
        if (direction == UiListDirection.VERTICAL) {
            float width = crossAlignment == UiListAlignment.STRETCH ? area.width() : current.width();
            return new UiRect(area.x() + crossOffset(area.width(), width), cursor, width, current.height());
        }
        float height = crossAlignment == UiListAlignment.STRETCH ? area.height() : current.height();
        return new UiRect(cursor, area.y() + crossOffset(area.height(), height), current.width(), height);
    }

    private float crossOffset(float available, float used) {
        return switch (crossAlignment) {
            case CENTER -> (available - used) * 0.5f;
            case END -> available - used;
            default -> 0.0f;
        };
    }
}
