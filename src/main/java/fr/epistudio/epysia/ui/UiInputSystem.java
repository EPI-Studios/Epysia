package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

public final class UiInputSystem implements GameSystem {

    private Window window;
    private boolean previousMouseDown;
    private UiButton armedButton;

    public UiInputSystem() {
    }

    @Override
    public void initialize(fr.epistudio.epysia.EngineServices services) {
        this.window = services.window();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        float cursorX = scaleCursor(input.cursorX(), window.framebufferWidth(), window.width());
        float cursorY = scaleCursor(input.cursorY(), window.framebufferHeight(), window.height());
        boolean mouseDown = input.isMouseButtonDown(MouseButton.LEFT);
        UiButton hovered = null;
        for (UiCanvasComponent canvas : scene.componentsOf(UiCanvasComponent.class)) {
            hovered = findHoveredButton(canvas.root(), cursorX, cursorY, hovered);
        }
        applyHoverAndClicks(hovered, mouseDown);
        previousMouseDown = mouseDown;
    }

    private float scaleCursor(float value, int framebufferDimension, int logicalDimension) {
        if (logicalDimension <= 0) {
            return value;
        }
        return value * ((float) framebufferDimension / (float) logicalDimension);
    }

    private UiButton findHoveredButton(UiNode node, float cursorX, float cursorY, UiButton current) {
        UiButton result = current;
        if (node instanceof UiButton button && node.visible() && node.computedRect().contains(cursorX, cursorY)) {
            result = button;
        }
        for (UiNode child : node.children()) {
            result = findHoveredButton(child, cursorX, cursorY, result);
        }
        return result;
    }

    private void applyHoverAndClicks(UiButton hovered, boolean mouseDown) {
        if (armedButton != null && armedButton != hovered) {
            armedButton.setHovered(false);
        }
        if (hovered != null) {
            hovered.setHovered(true);
        }
        boolean justPressed = mouseDown && !previousMouseDown;
        boolean justReleased = !mouseDown && previousMouseDown;
        if (justPressed && hovered != null) {
            hovered.setPressed(true);
            armedButton = hovered;
        }
        if (justReleased && armedButton != null) {
            if (armedButton == hovered) {
                armedButton.fireClick();
            }
            armedButton.setPressed(false);
            armedButton = null;
        }
    }
}
