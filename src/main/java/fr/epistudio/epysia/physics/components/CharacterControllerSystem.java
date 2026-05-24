package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.rapier.RapierCharacterController;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.CursorMode;
import fr.epistudio.epysia.window.Window;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CharacterControllerSystem implements GameSystem {

    private Window window;
    private PhysicsSystem physicsSystem;
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchHorizontal = new Vector3f();
    private final Vector3f scratchDesired = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();
    private float lastCursorX = Float.NaN;
    private float lastCursorY = Float.NaN;
    private boolean lookActive;

    public CharacterControllerSystem() {
    }

    @Override
    public void initialize(fr.epistudio.epysia.EngineServices services) {
        this.window = services.window();
        this.physicsSystem = services.systems().get(PhysicsSystem.class);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        updateLookCursorMode(input);
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(CharacterControllerComponent.class).ifPresent(component ->
                    gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                            updateCharacter(component, transform, input, deltaTimeSeconds)));
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

    private void updateCharacter(CharacterControllerComponent component, Transform3D transform,
                                 InputState input, float deltaTimeSeconds) {
        ensureAttached(component, transform);
        applyMouseLook(component, input);
        applyOrientation(component, transform);
        Vector3f desired = computeDesiredDisplacement(component, input, deltaTimeSeconds);
        RapierCharacterController.MoveResult result = component.nativeController()
                .move(component.bodyHandle(), desired, deltaTimeSeconds);
        applyMoveResult(component, transform, result);
    }

    private void ensureAttached(CharacterControllerComponent component, Transform3D transform) {
        if (component.nativeController() == null) {
            physicsSystem.attachCharacterController(component, transform);
        }
    }

    private void applyMouseLook(CharacterControllerComponent component, InputState input) {
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
        component.addYaw(-(cursorX - lastCursorX) * component.lookSensitivity());
        component.addPitch(-(cursorY - lastCursorY) * component.lookSensitivity());
        lastCursorX = cursorX;
        lastCursorY = cursorY;
    }

    private void applyOrientation(CharacterControllerComponent component, Transform3D transform) {
        scratchRotation.identity().rotateY(component.yawRadians()).rotateX(component.pitchRadians());
        transform.setRotation(scratchRotation);
    }

    private Vector3f computeDesiredDisplacement(CharacterControllerComponent component,
                                                InputState input, float deltaTimeSeconds) {
        scratchRotation.identity().rotateY(component.yawRadians());
        scratchRotation.transform(0.0f, 0.0f, -1.0f, scratchForward);
        scratchRotation.transform(1.0f, 0.0f, 0.0f, scratchRight);
        scratchHorizontal.set(0.0f);
        if (input.isKeyDown(KeyCode.W)) scratchHorizontal.add(scratchForward);
        if (input.isKeyDown(KeyCode.S)) scratchHorizontal.sub(scratchForward);
        if (input.isKeyDown(KeyCode.D)) scratchHorizontal.add(scratchRight);
        if (input.isKeyDown(KeyCode.A)) scratchHorizontal.sub(scratchRight);
        if (scratchHorizontal.lengthSquared() > 0.0f) {
            scratchHorizontal.normalize().mul(component.moveSpeed());
        }
        updateVerticalVelocity(component, input, deltaTimeSeconds);
        scratchDesired.set(
                scratchHorizontal.x * deltaTimeSeconds,
                component.verticalVelocity() * deltaTimeSeconds,
                scratchHorizontal.z * deltaTimeSeconds);
        return scratchDesired;
    }

    private void updateVerticalVelocity(CharacterControllerComponent component, InputState input, float deltaTimeSeconds) {
        if (component.grounded() && input.isKeyDown(KeyCode.SPACE)) {
            component.setVerticalVelocity(component.jumpSpeed());
            return;
        }
        component.setVerticalVelocity(component.verticalVelocity() + component.gravity() * deltaTimeSeconds);
    }

    private void applyMoveResult(CharacterControllerComponent component, Transform3D transform,
                                 RapierCharacterController.MoveResult result) {
        transform.translate(
                result.correctedDisplacement().x(),
                result.correctedDisplacement().y(),
                result.correctedDisplacement().z());
        component.setGrounded(result.grounded());
        if (result.grounded() && component.verticalVelocity() < 0.0f) {
            component.setVerticalVelocity(0.0f);
        }
        scratchPosition.set(transform.position());
        physicsSystem.world().setBodyPose(component.bodyHandle(), new RigidBodyPose(scratchPosition, transform.rotation()));
    }
}
