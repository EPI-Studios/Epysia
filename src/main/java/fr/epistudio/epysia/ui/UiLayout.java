package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.Export;

import java.util.List;

public abstract class UiLayout extends Component {

    @Export(label = "Padding", step = 1.0f)
    private float padding;

    public float padding() {
        return padding;
    }

    public UiLayout setPadding(float value) {
        padding = Math.max(0.0f, value);
        return this;
    }

    public abstract void arrange(UiRect containerRect, List<UiElement> children);

    protected UiRect inner(UiRect containerRect) {
        float doubled = padding * 2.0f;
        return new UiRect(containerRect.x() + padding, containerRect.y() + padding,
                Math.max(0.0f, containerRect.width() - doubled),
                Math.max(0.0f, containerRect.height() - doubled));
    }
}
