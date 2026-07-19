package fr.epistudio.epysia.physics.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

public final class CharacterControllerPhysicsSystem implements GameSystem {

    private PhysicsSystem physicsSystem;
    private final Vector3f scratchDesiredHorizontal = new Vector3f();
    private final Vector3f scratchTotalDesired = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();

    @Override
    public void initialize(EngineServices services) {
        this.physicsSystem = services.systems().get(PhysicsSystem.class);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(CharacterControllerComponent.class).ifPresent(component ->
                    gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                            updateCharacter(component, transform, deltaTimeSeconds)));
        }
    }

    private void updateCharacter(CharacterControllerComponent component, Transform3D transform,
                                 float deltaTimeSeconds) {
        ensureAttached(component, transform);
        updateVerticalVelocity(component, deltaTimeSeconds);
        component.consumeDesiredHorizontalMove(scratchDesiredHorizontal);
        scratchTotalDesired.set(
                scratchDesiredHorizontal.x,
                component.verticalVelocity() * deltaTimeSeconds,
                scratchDesiredHorizontal.z);
        Box3dCharacterController.MoveResult result = component.nativeController()
                .move(component.bodyHandle(), scratchTotalDesired, deltaTimeSeconds);
        transform.translate(
                result.correctedDisplacement().x(),
                result.correctedDisplacement().y(),
                result.correctedDisplacement().z());
        component.setGrounded(result.grounded());
        if (result.grounded() && component.verticalVelocity() < 0.0f) {
            component.setVerticalVelocity(0.0f);
        }
        scratchPosition.set(transform.position());
        physicsSystem.world().setBodyPose(component.bodyHandle(),
                new RigidBodyPose(scratchPosition, transform.rotation()));
    }

    private void updateVerticalVelocity(CharacterControllerComponent component, float deltaTimeSeconds) {
        if (component.consumeJumpRequest() && component.grounded()) {
            component.setVerticalVelocity(component.jumpSpeed());
            return;
        }
        component.setVerticalVelocity(component.verticalVelocity() + component.gravity() * deltaTimeSeconds);
    }

    private void ensureAttached(CharacterControllerComponent component, Transform3D transform) {
        if (component.nativeController() == null) {
            physicsSystem.attachCharacterController(component, transform);
        }
    }
}
