package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Scroll View", category = "UI")
public final class UiScrollView extends UiElement {
    private static final float DEFAULT_SPEED = 40.0f;

    @Export(label = "Scroll speed", min = 1.0f, max = 400.0f, step = 1.0f)
    private float scrollSpeed = DEFAULT_SPEED;
    @Export(label = "Background", color = true)
    private final Vector4f backgroundColor = new Vector4f(0.08f, 0.09f, 0.11f, 0.6f);

    private float scrollOffset;
    private float contentExtent;

    public UiScrollView() {
        setClipChildren(true);
    }

    public float scrollOffset() {
        return scrollOffset;
    }

    public float contentExtent() {
        return contentExtent;
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public boolean wantsWheel() {
        return true;
    }

    @Override
    protected void paint(UiPainter painter) {
        painter.fillRect(computedRect(), UiColors.of(backgroundColor));
    }

    @Override
    protected UiRect childParentRect() {
        return contentRect();
    }

    @Override
    protected void onChildrenLaidOut() {
        measureContent();
    }

    public UiRect contentRect() {
        UiRect rect = computedRect();
        return new UiRect(rect.x(), rect.y() - scrollOffset, rect.width(), rect.height());
    }

    public void measureContent() {
        float extent = 0.0f;
        UiRect content = contentRect();
        for (UiElement child : children()) {
            extent = Math.max(extent, child.computedRect().y() + child.computedRect().height() - content.y());
        }
        contentExtent = extent;
    }

    @Override
    protected void onWheel(float amount) {
        float maximum = Math.max(0.0f, contentExtent - computedRect().height());
        scrollOffset = Math.clamp(scrollOffset - amount * scrollSpeed, 0.0f, maximum);
    }
}
