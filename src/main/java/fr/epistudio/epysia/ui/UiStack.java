package fr.epistudio.epysia.ui;

public final class UiStack extends UiNode {

    private UiStackAxis axis = UiStackAxis.VERTICAL;
    private float spacing = 4.0f;
    private float paddingX = 0.0f;
    private float paddingY = 0.0f;
    private UiStackAlignment crossAxisAlignment = UiStackAlignment.START;

    public UiStack setAxis(UiStackAxis axis) {
        this.axis = axis;
        return this;
    }

    public UiStack setSpacing(float spacing) {
        this.spacing = spacing;
        return this;
    }

    public UiStack setPadding(float padding) {
        this.paddingX = padding;
        this.paddingY = padding;
        return this;
    }

    public UiStack setCrossAxisAlignment(UiStackAlignment alignment) {
        this.crossAxisAlignment = alignment;
        return this;
    }

    @Override
    protected void layoutChildren() {
        UiRect rect = computedRect();
        float innerX = rect.x() + paddingX;
        float innerY = rect.y() + paddingY;
        float innerWidth = Math.max(0.0f, rect.width() - paddingX * 2.0f);
        float innerHeight = Math.max(0.0f, rect.height() - paddingY * 2.0f);
        if (axis == UiStackAxis.VERTICAL) {
            layoutVertical(innerX, innerY, innerWidth, innerHeight);
        } else {
            layoutHorizontal(innerX, innerY, innerWidth, innerHeight);
        }
    }

    private void layoutVertical(float innerX, float innerY, float innerWidth, float innerHeight) {
        float cursor = innerY;
        for (UiNode child : children()) {
            float crossOffset = (innerWidth - child.width()) * crossAxisAlignment.relativePosition();
            child.setComputedRect(new UiRect(innerX + crossOffset, cursor, child.width(), child.height()));
            child.layoutChildren();
            cursor += child.height() + spacing;
        }
    }

    private void layoutHorizontal(float innerX, float innerY, float innerWidth, float innerHeight) {
        float cursor = innerX;
        for (UiNode child : children()) {
            float crossOffset = (innerHeight - child.height()) * crossAxisAlignment.relativePosition();
            child.setComputedRect(new UiRect(cursor, innerY + crossOffset, child.width(), child.height()));
            child.layoutChildren();
            cursor += child.width() + spacing;
        }
    }
}
