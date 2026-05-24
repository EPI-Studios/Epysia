package fr.epistudio.epysia.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.CursorMode;
import fr.epistudio.epysia.window.Window;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FlyCameraSystem implements GameSystem {

    private Window window;
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchMovement = new Vector3f();
    private float lastCursorX = Float.NaN;
    private float lastCursorY = Float.NaN;
    private boolean lookActive;

    public FlyCameraSystem() {
    }

    @Override
    public void initialize(fr.epistudio.epysia.EngineServices services) {
        this.window = services.window();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        updateLookCursorMode(input);
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(FlyCameraComponent.class).ifPresent(controller ->
                    gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                            updateCamera(controller, transform, input, deltaTimeSeconds)));
        }
    }

    private void updateLookCursorMode(InputState input) {
        boolean shouldLook = input.isMouseButtonDown(MouseButton.RIGHT);
        if (shouldLook == lookActive) {
            return;
        }
        lookActive = shouldLook;
        window.setCursorMode(shouldLook ? CursorMode.DISABLED : CursorMode.NORMAL);
        lastCursorX = Float.NaN;
        lastCursorY = Float.NaN;
    }

    private void updateCamera(FlyCameraComponent controller, Transform3D transform, InputState input, float deltaTimeSeconds) {
        applyMouseLook(controller, input);
        applyOrientation(controller, transform);
        applyMovement(controller, transform, input, deltaTimeSeconds);
    }

    private void applyMouseLook(FlyCameraComponent controller, InputState input) {
        if (!lookActive) {
            return;
        }
        float cursorX = input.cursorX();
        float cursorY = input.cursorY();
        if (Float.isNaN(lastCursorX)) {
            lastCursorX = cursorX;
            lastCursorY = cursorY;
            return;
        }
        float deltaX = cursorX - lastCursorX;
        float deltaY = cursorY - lastCursorY;
        controller.addYaw(-deltaX * controller.lookSensitivity());
        controller.addPitch(-deltaY * controller.lookSensitivity());
        lastCursorX = cursorX;
        lastCursorY = cursorY;
    }

    private void applyOrientation(FlyCameraComponent controller, Transform3D transform) {
        scratchRotation.identity().rotateY(controller.yawRadians()).rotateX(controller.pitchRadians());
        transform.setRotation(scratchRotation);
    }

    private void applyMovement(FlyCameraComponent controller, Transform3D transform, InputState input, float deltaTimeSeconds) {
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        transform.rotation().transform(1.0f, 0.0f, 0.0f, scratchRight);
        scratchMovement.set(0.0f, 0.0f, 0.0f);
        if (input.isKeyDown(KeyCode.W)) scratchMovement.add(scratchForward);
        if (input.isKeyDown(KeyCode.S)) scratchMovement.sub(scratchForward);
        if (input.isKeyDown(KeyCode.D)) scratchMovement.add(scratchRight);
        if (input.isKeyDown(KeyCode.A)) scratchMovement.sub(scratchRight);
        if (input.isKeyDown(KeyCode.SPACE)) scratchMovement.add(0.0f, 1.0f, 0.0f);
        if (input.isKeyDown(KeyCode.LEFT_SHIFT)) scratchMovement.sub(0.0f, 1.0f, 0.0f);
        if (scratchMovement.lengthSquared() <= 0.0f) {
            return;
        }
        float speed = controller.moveSpeed();
        if (input.isKeyDown(KeyCode.LEFT_CONTROL)) {
            speed *= controller.boostMultiplier();
        }
        scratchMovement.normalize().mul(speed * deltaTimeSeconds);
        transform.translate(scratchMovement.x, scratchMovement.y, scratchMovement.z);
    }
}
