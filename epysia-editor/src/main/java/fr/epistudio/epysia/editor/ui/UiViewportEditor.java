package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.UiRectDragCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.ui.UiCanvas;
import fr.epistudio.epysia.ui.UiElement;
import fr.epistudio.epysia.ui.UiRect;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UiViewportEditor {

    public static final int HANDLE_COUNT = 8;

    private static final int OUTLINE_COLOR = 0xFF3FA9F5;
    private static final int PARENT_COLOR = 0x553FA9F5;
    private static final int HANDLE_COLOR = 0xFFF5F5F5;
    private static final int LABEL_BACKGROUND = 0xFF1B1E26;
    private static final int LABEL_TEXT = 0xFFE6E8EE;
    private static final float OUTLINE_THICKNESS = 1.5f;
    private static final float HANDLE_SIZE = 4.0f;
    private static final float HANDLE_PICK_RADIUS = 6.0f;
    private static final float MINIMUM_SIZE = 4.0f;
    private static final int[] HORIZONTAL_SIGN = {-1, 0, 1, 1, 1, 0, -1, -1};
    private static final int[] VERTICAL_SIGN = {-1, -1, -1, 0, 1, 1, 1, 0};

    private boolean dragging;
    private int activeHandle = -1;
    private float dragStartX;
    private float dragStartY;
    private final Vector4f startPosition = new Vector4f();
    private final Vector4f startSize = new Vector4f();
    private UiElement dragged;

    public boolean render(Scene scene, GameObject selected, EditorHistory history,
                          float imageX, float imageY, boolean hovered) {
        Optional<UiCanvas> canvas = firstCanvas(scene);
        if (canvas.isEmpty()) {
            return false;
        }
        float scale = canvas.get().scaleFactor();
        Optional<UiElement> element = Optional.ofNullable(selected)
                .flatMap(object -> object.getComponent(UiElement.class));
        element.ifPresent(target -> drawChrome(target, imageX, imageY, scale));
        if (!hovered) {
            return dragging;
        }
        return handleDrag(element.orElse(null), history, imageX, imageY, scale);
    }

    public Optional<GameObject> pick(Scene scene, float imageX, float imageY,
                                     float pointerX, float pointerY) {
        Optional<UiCanvas> canvas = firstCanvas(scene);
        if (canvas.isEmpty()) {
            return Optional.empty();
        }
        float scale = canvas.get().scaleFactor();
        float designX = (pointerX - imageX) / scale;
        float designY = (pointerY - imageY) / scale;
        List<UiElement> ordered = new ArrayList<>();
        for (UiElement root : canvas.get().roots()) {
            collect(root, ordered);
        }
        for (int index = ordered.size() - 1; index >= 0; index--) {
            UiElement candidate = ordered.get(index);
            if (candidate.drawable() && candidate.computedRect().contains(designX, designY)) {
                return candidate.owner();
            }
        }
        return Optional.empty();
    }

    private void collect(UiElement element, List<UiElement> into) {
        into.add(element);
        for (UiElement child : element.children()) {
            collect(child, into);
        }
    }

    private static Optional<UiCanvas> firstCanvas(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            if (!gameObject.active()) {
                continue;
            }
            Optional<UiCanvas> canvas = gameObject.getComponent(UiCanvas.class).filter(UiCanvas::visible);
            if (canvas.isPresent()) {
                return canvas;
            }
        }
        return Optional.empty();
    }

    private void drawChrome(UiElement element, float imageX, float imageY, float scale) {
        parentOf(element).ifPresent(parent ->
                drawOutline(parent.computedRect(), imageX, imageY, scale, PARENT_COLOR, 1.0f));
        UiRect rect = element.computedRect();
        drawOutline(rect, imageX, imageY, scale, OUTLINE_COLOR, OUTLINE_THICKNESS);
        for (int handle = 0; handle < HANDLE_COUNT; handle++) {
            float x = handleX(rect, handle, imageX, scale);
            float y = handleY(rect, handle, imageY, scale);
            ImGui.getWindowDrawList().addRectFilled(x - HANDLE_SIZE, y - HANDLE_SIZE,
                    x + HANDLE_SIZE, y + HANDLE_SIZE, HANDLE_COLOR);
        }
        drawSizeLabel(rect, imageX, imageY, scale);
    }

    private void drawOutline(UiRect rect, float imageX, float imageY, float scale,
                             int color, float thickness) {
        ImGui.getWindowDrawList().addRect(imageX + rect.x() * scale, imageY + rect.y() * scale,
                imageX + (rect.x() + rect.width()) * scale, imageY + (rect.y() + rect.height()) * scale,
                color, 0.0f, 0, thickness);
    }

    private void drawSizeLabel(UiRect rect, float imageX, float imageY, float scale) {
        String label = Math.round(rect.width()) + " x " + Math.round(rect.height());
        float x = imageX + (rect.x() + rect.width()) * scale;
        float y = imageY + (rect.y() + rect.height()) * scale + 4.0f;
        float width = ImGui.calcTextSizeX(label) + 8.0f;
        ImGui.getWindowDrawList().addRectFilled(x - width, y, x, y + 16.0f, LABEL_BACKGROUND, 3.0f);
        ImGui.getWindowDrawList().addText(x - width + 4.0f, y + 1.0f, LABEL_TEXT, label);
    }

    private boolean handleDrag(UiElement element, EditorHistory history,
                               float imageX, float imageY, float scale) {
        if (element == null) {
            return false;
        }
        if (!dragging && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            beginDrag(element, imageX, imageY, scale);
        }
        if (!dragging) {
            return false;
        }
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            applyDrag(scale);
            return true;
        }
        endDrag(history);
        return false;
    }

    private void beginDrag(UiElement element, float imageX, float imageY, float scale) {
        float pointerX = ImGui.getMousePosX();
        float pointerY = ImGui.getMousePosY();
        UiRect rect = element.computedRect();
        activeHandle = handleAt(rect, imageX, imageY, scale, pointerX, pointerY);
        boolean insideBody = rect.contains((pointerX - imageX) / scale, (pointerY - imageY) / scale);
        if (activeHandle < 0 && !insideBody) {
            return;
        }
        dragging = true;
        dragged = element;
        dragStartX = pointerX;
        dragStartY = pointerY;
        startPosition.set(element.position());
        startSize.set(element.size());
    }

    private void applyDrag(float scale) {
        float deltaX = (ImGui.getMousePosX() - dragStartX) / scale;
        float deltaY = (ImGui.getMousePosY() - dragStartY) / scale;
        if (activeHandle < 0) {
            dragged.position().set(startPosition.x(), startPosition.y() + deltaX,
                    startPosition.z(), startPosition.w() + deltaY);
            return;
        }
        resize(deltaX, deltaY);
    }

    private void resize(float deltaX, float deltaY) {
        Vector2f anchor = dragged.anchorPoint();
        float sizeOffsetX = startSize.y();
        float sizeOffsetY = startSize.w();
        float positionOffsetX = startPosition.y();
        float positionOffsetY = startPosition.w();
        if (HORIZONTAL_SIGN[activeHandle] < 0) {
            float applied = Math.min(deltaX, sizeOffsetX - MINIMUM_SIZE);
            sizeOffsetX -= applied;
            positionOffsetX += applied * (1.0f - anchor.x());
        } else if (HORIZONTAL_SIGN[activeHandle] > 0) {
            float applied = Math.max(deltaX, MINIMUM_SIZE - sizeOffsetX);
            sizeOffsetX += applied;
            positionOffsetX += applied * anchor.x();
        }
        if (VERTICAL_SIGN[activeHandle] < 0) {
            float applied = Math.min(deltaY, sizeOffsetY - MINIMUM_SIZE);
            sizeOffsetY -= applied;
            positionOffsetY += applied * (1.0f - anchor.y());
        } else if (VERTICAL_SIGN[activeHandle] > 0) {
            float applied = Math.max(deltaY, MINIMUM_SIZE - sizeOffsetY);
            sizeOffsetY += applied;
            positionOffsetY += applied * anchor.y();
        }
        dragged.size().set(startSize.x(), sizeOffsetX, startSize.z(), sizeOffsetY);
        dragged.position().set(startPosition.x(), positionOffsetX, startPosition.z(), positionOffsetY);
    }

    private void endDrag(EditorHistory history) {
        if (dragged != null && changedSinceStart()) {
            history.execute(new UiRectDragCommand(dragged, new Vector4f(startPosition),
                    new Vector4f(startSize), new Vector4f(dragged.position()),
                    new Vector4f(dragged.size())));
        }
        dragging = false;
        activeHandle = -1;
        dragged = null;
    }

    private boolean changedSinceStart() {
        return !dragged.position().equals(startPosition) || !dragged.size().equals(startSize);
    }

    private int handleAt(UiRect rect, float imageX, float imageY, float scale,
                         float pointerX, float pointerY) {
        for (int handle = 0; handle < HANDLE_COUNT; handle++) {
            float x = handleX(rect, handle, imageX, scale);
            float y = handleY(rect, handle, imageY, scale);
            if (Math.abs(pointerX - x) <= HANDLE_PICK_RADIUS && Math.abs(pointerY - y) <= HANDLE_PICK_RADIUS) {
                return handle;
            }
        }
        return -1;
    }

    private static float handleX(UiRect rect, int handle, float imageX, float scale) {
        float relative = (HORIZONTAL_SIGN[handle] + 1) * 0.5f;
        return imageX + (rect.x() + rect.width() * relative) * scale;
    }

    private static float handleY(UiRect rect, int handle, float imageY, float scale) {
        float relative = (VERTICAL_SIGN[handle] + 1) * 0.5f;
        return imageY + (rect.y() + rect.height() * relative) * scale;
    }

    private static Optional<UiElement> parentOf(UiElement element) {
        return element.owner()
                .flatMap(owner -> owner.getComponent(Transform3D.class))
                .flatMap(Transform3D::parent)
                .flatMap(Transform3D::owner)
                .flatMap(owner -> owner.getComponent(UiElement.class));
    }
}
