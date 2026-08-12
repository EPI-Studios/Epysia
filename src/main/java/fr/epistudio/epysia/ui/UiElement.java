package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.KeyCode;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiresComponent(Transform3D.class)
public abstract class UiElement extends Component {
    @Export(label = "Position", step = 1.0f)
    private final Vector4f position = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    @Export(label = "Size", step = 1.0f)
    private final Vector4f size = new Vector4f(0.0f, 160.0f, 0.0f, 48.0f);
    @Export(label = "Anchor point", min = 0.0f, max = 1.0f, step = 0.05f)
    private final Vector2f anchorPoint = new Vector2f();
    @Export(label = "Z index", step = 1.0f)
    private int zIndex;
    @Export(label = "Visible")
    private boolean visible = true;
    @Export(label = "Clip children")
    private boolean clipChildren;

    private UiRect computedRect = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    public Vector4f position() {
        return position;
    }

    public Vector4f size() {
        return size;
    }

    public Vector2f anchorPoint() {
        return anchorPoint;
    }

    public int zIndex() {
        return zIndex;
    }

    public boolean visible() {
        return visible;
    }

    public boolean drawable() {
        return visible && enabled() && owner().map(GameObject::activeInHierarchy).orElse(true);
    }

    public Optional<UiShader> shader() {
        return Optional.empty();
    }

    public boolean clipChildren() {
        return clipChildren;
    }

    public UiElement setPosition(float scaleX, float offsetX, float scaleY, float offsetY) {
        position.set(scaleX, offsetX, scaleY, offsetY);
        return this;
    }

    public UiElement setSize(float scaleX, float offsetX, float scaleY, float offsetY) {
        size.set(scaleX, offsetX, scaleY, offsetY);
        return this;
    }

    public UiElement setAnchorPoint(float x, float y) {
        anchorPoint.set(x, y);
        return this;
    }

    public UiElement setZIndex(int value) {
        this.zIndex = value;
        return this;
    }

    public UiElement setVisible(boolean value) {
        this.visible = value;
        return this;
    }

    public UiElement setClipChildren(boolean value) {
        this.clipChildren = value;
        return this;
    }

    public UiRect computedRect() {
        return computedRect;
    }

    public UiRect hitRect() {
        return computedRect;
    }

    public boolean interactive() {
        return false;
    }

    public boolean wantsKeyboard() {
        return false;
    }

    public boolean wantsWheel() {
        return false;
    }

    public final void layout(UiRect parentRect) {
        computedRect = computeRect(parentRect);
        UiRect childParentRect = childParentRect();
        for (UiElement child : children()) {
            child.layout(childParentRect);
        }
        onChildrenLaidOut();
    }

    protected UiRect childParentRect() {
        return computedRect;
    }

    protected void onChildrenLaidOut() {
    }

    protected UiRect computeRect(UiRect parentRect) {
        float width = size.x() * parentRect.width() + size.y();
        float height = size.z() * parentRect.height() + size.w();
        float x = parentRect.x() + position.x() * parentRect.width() + position.y()
                - width * anchorPoint.x();
        float y = parentRect.y() + position.z() * parentRect.height() + position.w()
                - height * anchorPoint.y();
        return new UiRect(x, y, Math.max(0.0f, width), Math.max(0.0f, height));
    }

    public List<UiElement> children() {
        List<UiElement> found = new ArrayList<>();
        Optional<Transform3D> transform = owner().flatMap(owner -> owner.getComponent(Transform3D.class));
        if (transform.isEmpty()) {
            return found;
        }
        for (Transform3D child : transform.get().children()) {
            child.owner().flatMap(owner -> owner.getComponent(UiElement.class)).ifPresent(found::add);
        }
        found.sort((first, second) -> Integer.compare(first.zIndex, second.zIndex));
        return found;
    }

    public String displayName() {
        return owner().map(GameObject::name).orElse("");
    }

    protected void paint(UiPainter painter) {
    }

    protected void onPointerDown(float localX, float localY) {
    }

    protected void onPointerDrag(float localX, float localY) {
    }

    protected void onPointerUp(float localX, float localY, boolean inside) {
    }

    protected void onHoverChanged(boolean hovered) {
    }

    protected void onWheel(float amount) {
    }

    protected void onText(String typed) {
    }

    protected void onKey(KeyCode key) {
    }

    protected void onFocusChanged(boolean focused) {
    }

    public final void paintInto(UiPainter painter) {
        paint(painter);
    }

    public final void dispatchPointerDown(float localX, float localY) {
        onPointerDown(localX, localY);
    }

    public final void dispatchPointerDrag(float localX, float localY) {
        onPointerDrag(localX, localY);
    }

    public final void dispatchPointerUp(float localX, float localY, boolean inside) {
        onPointerUp(localX, localY, inside);
    }

    public final void dispatchHoverChanged(boolean hovered) {
        onHoverChanged(hovered);
    }

    public final void dispatchWheel(float amount) {
        onWheel(amount);
    }

    public final void dispatchText(String typed) {
        onText(typed);
    }

    public final void dispatchKey(KeyCode key) {
        onKey(key);
    }

    public final void dispatchFocusChanged(boolean focused) {
        onFocusChanged(focused);
    }
}
