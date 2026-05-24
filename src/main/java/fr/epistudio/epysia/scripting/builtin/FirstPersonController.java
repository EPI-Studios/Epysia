package fr.epistudio.epysia.scripting.builtin;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.scripting.Behaviour;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "First Person Controller", category = "Game")
public final class FirstPersonController extends Behaviour {

    private static final float MAX_PITCH_RADIANS = (float) Math.toRadians(89.0);

    @Export(label = "Move Speed", min = 0.0f, max = 30.0f, step = 0.1f)
    private float moveSpeed = 5.0f;
    @Export(label = "Mouse Sensitivity", min = 0.0001f, max = 0.02f, step = 0.0001f)
    private float mouseSensitivity = 0.0025f;
    @Export(label = "Jump Boost", min = 0.0f, max = 5.0f, step = 0.1f)
    private float jumpExtraMultiplier = 1.0f;

    private CharacterControllerComponent controller;
    private Transform3D transform;
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchDesired = new Vector3f();
    private float yawRadians;
    private float pitchRadians;
    private float lastCursorX = Float.NaN;
    private float lastCursorY = Float.NaN;
    private boolean lookActive;

    @Override
    public void onStart(EngineServices services) {
        GameObject self = owner().orElseThrow();
        controller = self.getComponent(CharacterControllerComponent.class).orElseThrow();
        transform = self.getComponent(Transform3D.class).orElseThrow();
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        if (controller == null || transform == null) {
            return;
        }
        updateMouseLook(input);
        applyOrientationToTransform();
        applyMovementToController(input, deltaTimeSeconds);
        if (input.isKeyDown(KeyCode.SPACE) && controller.grounded()) {
            controller.requestJump();
            if (jumpExtraMultiplier > 1.0f) {
                controller.setVerticalVelocity(controller.jumpSpeed() * jumpExtraMultiplier);
            }
        }
    }

    private void updateMouseLook(InputState input) {
        boolean rightHeld = input.isMouseButtonDown(MouseButton.RIGHT);
        if (!rightHeld) {
            lookActive = false;
            return;
        }
        float cursorX = input.cursorX();
        float cursorY = input.cursorY();
        if (!lookActive) {
            lookActive = true;
            lastCursorX = cursorX;
            lastCursorY = cursorY;
            return;
        }
        yawRadians -= (cursorX - lastCursorX) * mouseSensitivity;
        pitchRadians = clamp(pitchRadians - (cursorY - lastCursorY) * mouseSensitivity,
                -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        lastCursorX = cursorX;
        lastCursorY = cursorY;
    }

    private void applyOrientationToTransform() {
        scratchRotation.identity().rotateY(yawRadians).rotateX(pitchRadians);
        transform.setRotation(scratchRotation);
    }

    private void applyMovementToController(InputState input, float deltaTimeSeconds) {
        scratchRotation.identity().rotateY(yawRadians);
        scratchRotation.transform(0.0f, 0.0f, -1.0f, scratchForward);
        scratchRotation.transform(1.0f, 0.0f, 0.0f, scratchRight);
        scratchDesired.set(0.0f);
        if (input.isKeyDown(KeyCode.W)) scratchDesired.add(scratchForward);
        if (input.isKeyDown(KeyCode.S)) scratchDesired.sub(scratchForward);
        if (input.isKeyDown(KeyCode.D)) scratchDesired.add(scratchRight);
        if (input.isKeyDown(KeyCode.A)) scratchDesired.sub(scratchRight);
        if (scratchDesired.lengthSquared() > 0.0f) {
            scratchDesired.normalize().mul(moveSpeed * deltaTimeSeconds);
        }
        controller.setDesiredHorizontalMove(scratchDesired);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
