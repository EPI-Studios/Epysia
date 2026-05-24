package fr.epistudio.epysia.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract sealed class UiNode permits UiPanel, UiLabel, UiButton, UiImage, UiStack {

    private float offsetX;
    private float offsetY;
    private float width;
    private float height;
    private UiAnchor anchor = UiAnchor.TOP_LEFT;
    private boolean visible = true;
    private final List<UiNode> children = new ArrayList<>();
    private UiRect computedRect = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiShader customShader;

    public UiNode setOffset(float x, float y) {
        this.offsetX = x;
        this.offsetY = y;
        return this;
    }

    public UiNode setSize(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public UiNode setAnchor(UiAnchor anchor) {
        this.anchor = anchor;
        return this;
    }

    public UiNode setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public UiNode setShader(UiShader shader) {
        this.customShader = shader;
        return this;
    }

    public Optional<UiShader> customShader() {
        return Optional.ofNullable(customShader);
    }

    public UiNode addChild(UiNode child) {
        children.add(child);
        return this;
    }

    public List<UiNode> children() {
        return children;
    }

    public boolean visible() {
        return visible;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public UiAnchor anchor() {
        return anchor;
    }

    public UiRect computedRect() {
        return computedRect;
    }

    public final void layout(UiRect parentRect) {
        computedRect = computeOwnRect(parentRect);
        layoutChildren();
    }

    protected UiRect computeOwnRect(UiRect parentRect) {
        float originX = parentRect.x() + parentRect.width() * anchor.relativeX();
        float originY = parentRect.y() + parentRect.height() * anchor.relativeY();
        float pivotX = width * anchor.relativeX();
        float pivotY = height * anchor.relativeY();
        return new UiRect(originX + offsetX - pivotX, originY + offsetY - pivotY, width, height);
    }

    protected void layoutChildren() {
        for (UiNode child : children) {
            child.layout(computedRect);
        }
    }

    protected final void setComputedRect(UiRect rect) {
        this.computedRect = rect;
    }
}
