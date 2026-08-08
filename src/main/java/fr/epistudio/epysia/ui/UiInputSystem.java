package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

import java.util.Optional;
import fr.epistudio.epysia.components.transforms.Transform3D;

public final class UiInputSystem implements GameSystem {
    private static final KeyCode[] FORWARDED_KEYS = {
            KeyCode.BACKSPACE, KeyCode.DELETE, KeyCode.ENTER, KeyCode.ESCAPE,
            KeyCode.ARROW_LEFT, KeyCode.ARROW_RIGHT, KeyCode.ARROW_UP, KeyCode.ARROW_DOWN, KeyCode.TAB};

    private Window window;
    private UiElement hovered;
    private UiElement pressed;
    private UiElement focused;
    private boolean previousMouseDown;
    private boolean pointerOverUi;

    @Override
    public void initialize(EngineServices services) {
        this.window = services.window();
    }

    public boolean pointerOverUi() {
        return pointerOverUi;
    }

    public Optional<UiElement> focused() {
        return Optional.ofNullable(focused);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        UiHit hit = findHit(scene, input);
        pointerOverUi = hit.element() != null;
        applyHover(hit.element());
        applyPointer(hit, input);
        applyWheel(hit, input);
        applyKeyboard(input);
    }

    private UiHit findHit(Scene scene, InputState input) {
        float cursorX = scaleCursor(input.cursorX(), window.framebufferWidth(), window.width());
        float cursorY = scaleCursor(input.cursorY(), window.framebufferHeight(), window.height());
        UiHit best = new UiHit(null, 0.0f, 0.0f);
        for (UiCanvas canvas : scene.componentsOf(UiCanvas.class)) {
            float scale = canvas.scaleFactor();
            UiRect viewport = new UiRect(0.0f, 0.0f, window.framebufferWidth() / scale,
                    window.framebufferHeight() / scale);
            for (UiElement root : canvas.roots()) {
                UiHit found = UiHitTest.topmost(root, cursorX / scale, cursorY / scale, viewport);
                if (found.element() != null) {
                    best = found;
                }
            }
        }
        return best;
    }

    private float scaleCursor(float value, int framebufferDimension, int logicalDimension) {
        if (logicalDimension <= 0) {
            return value;
        }
        return value * ((float) framebufferDimension / (float) logicalDimension);
    }

    private void applyHover(UiElement node) {
        if (hovered == node) {
            return;
        }
        if (hovered != null) {
            hovered.dispatchHoverChanged(false);
        }
        if (node != null) {
            node.dispatchHoverChanged(true);
        }
        hovered = node;
    }

    private void applyPointer(UiHit hit, InputState input) {
        boolean mouseDown = input.isMouseButtonDown(MouseButton.LEFT);
        if (mouseDown && !previousMouseDown) {
            beginPress(hit);
        } else if (mouseDown && pressed != null) {
            pressed.dispatchPointerDrag(hit.localX(), hit.localY());
        } else if (!mouseDown && previousMouseDown && pressed != null) {
            pressed.dispatchPointerUp(hit.localX(), hit.localY(), pressed == hit.element());
            pressed = null;
        }
        previousMouseDown = mouseDown;
    }

    private void beginPress(UiHit hit) {
        applyFocus(hit.element());
        if (hit.element() == null) {
            return;
        }
        pressed = hit.element();
        pressed.dispatchPointerDown(hit.localX(), hit.localY());
    }

    private void applyFocus(UiElement node) {
        UiElement next = node != null && node.wantsKeyboard() ? node : null;
        if (focused == next) {
            return;
        }
        if (focused != null) {
            focused.dispatchFocusChanged(false);
        }
        if (next != null) {
            next.dispatchFocusChanged(true);
        }
        focused = next;
    }

    private void applyWheel(UiHit hit, InputState input) {
        float wheel = input.scrollDeltaY();
        if (wheel == 0.0f || hit.element() == null) {
            return;
        }
        UiElement walker = hit.element();
        while (walker != null) {
            if (walker.wantsWheel()) {
                walker.dispatchWheel(wheel);
                return;
            }
            walker = parentOf(walker);
        }
    }

    private static UiElement parentOf(UiElement element) {
        return element.owner()
                .flatMap(owner -> owner.getComponent(Transform3D.class))
                .flatMap(Transform3D::parent)
                .flatMap(parent -> parent.owner())
                .flatMap(owner -> owner.getComponent(UiElement.class))
                .orElse(null);
    }

    private void applyKeyboard(InputState input) {
        if (focused == null) {
            return;
        }
        String typed = input.typedText();
        if (!typed.isEmpty()) {
            focused.dispatchText(typed);
        }
        for (KeyCode key : FORWARDED_KEYS) {
            if (input.wasKeyPressed(key) || input.wasKeyRepeated(key)) {
                focused.dispatchKey(key);
            }
        }
    }
}
