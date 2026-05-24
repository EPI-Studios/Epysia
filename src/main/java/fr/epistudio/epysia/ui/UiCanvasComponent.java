package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.Component;

public final class UiCanvasComponent extends Component {

    private final UiPanel root = (UiPanel) new UiPanel()
            .setColor(UiColor.TRANSPARENT)
            .setAnchor(UiAnchor.TOP_LEFT);

    public UiPanel root() {
        return root;
    }

    public void resizeToViewport(float width, float height) {
        root.setSize(width, height);
        root.layout(new UiRect(0.0f, 0.0f, width, height));
    }
}
