package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

import java.util.List;

@EpysiaComponent(name = "Ui Grid Layout", category = "UI")
public final class UiGridLayout extends UiLayout {

    @Export(label = "Cell width", step = 1.0f)
    private float cellWidth = 96.0f;

    @Export(label = "Cell height", step = 1.0f)
    private float cellHeight = 96.0f;

    @Export(label = "Spacing", step = 1.0f)
    private float spacing = 4.0f;

    @Export(label = "Columns", min = 0.0f, max = 64.0f, step = 1.0f)
    private int columns;

    public UiGridLayout setCellSize(float width, float height) {
        cellWidth = Math.max(1.0f, width);
        cellHeight = Math.max(1.0f, height);
        return this;
    }

    public UiGridLayout setSpacing(float value) {
        spacing = Math.max(0.0f, value);
        return this;
    }

    public UiGridLayout setColumns(int value) {
        columns = Math.max(0, value);
        return this;
    }

    public int columns() {
        return columns;
    }

    @Override
    public void arrange(UiRect containerRect, List<UiElement> children) {
        UiRect area = inner(containerRect);
        int perRow = resolveColumns(area.width());
        int index = 0;
        for (UiElement child : children) {
            if (!child.visible()) {
                continue;
            }
            int column = index % perRow;
            int row = index / perRow;
            child.placeAt(new UiRect(
                    area.x() + column * (cellWidth + spacing),
                    area.y() + row * (cellHeight + spacing),
                    cellWidth, cellHeight));
            index++;
        }
    }

    private int resolveColumns(float availableWidth) {
        if (columns > 0) {
            return columns;
        }
        int fitting = (int) Math.floor((availableWidth + spacing) / (cellWidth + spacing));
        return Math.max(1, fitting);
    }
}
