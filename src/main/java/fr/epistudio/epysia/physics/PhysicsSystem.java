package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.QueryFilter;
import fr.epistudio.epysia.physics.api.RaycastHit;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.physics.rapier.RapierCharacterController;
import fr.epistudio.epysia.physics.rapier.RapierPhysicsWorld;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PhysicsSystem implements GameSystem {

    private final Vector3f defaultGravity = new Vector3f(0.0f, -9.81f, 0.0f);
    private final Vector3f scratchPosition = new Vector3f();
    private final Quaternionf scratchRotation = new Quaternionf();
    private final List<RapierCharacterController> ownedControllers = new ArrayList<>();
    private RapierPhysicsWorld world;

    @Override
    public void initialize(fr.epistudio.epysia.EngineServices services) {
        world = new RapierPhysicsWorld();
        world.setGravity(defaultGravity);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (world == null) {
            return;
        }
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(RigidBodyComponent.class).ifPresent(rigidBody ->
                    syncRigidBodyToPhysics(gameObject, rigidBody));
        }
        world.step(deltaTimeSeconds);
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(RigidBodyComponent.class).ifPresent(rigidBody ->
                    pullDynamicTransform(gameObject, rigidBody));
        }
    }

    private void syncRigidBodyToPhysics(GameObject gameObject, RigidBodyComponent rigidBody) {
        Transform3D transform = gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("RigidBodyComponent requires Transform3D on " + gameObject.name()));
        if (!rigidBody.isRegistered()) {
            if (rigidBody.shape() == null) {
                return;
            }
            registerRigidBody(rigidBody, transform);
            return;
        }
        if (rigidBody.kind() == RigidBodyKind.KINEMATIC || rigidBody.kind() == RigidBodyKind.STATIC) {
            scratchPosition.set(transform.position());
            scratchRotation.set(transform.rotation());
            world.setBodyPose(rigidBody.handle(), new RigidBodyPose(scratchPosition, scratchRotation));
        }
    }

    private void registerRigidBody(RigidBodyComponent rigidBody, Transform3D transform) {
        RigidBodyPose pose = new RigidBodyPose(new Vector3f(transform.position()), new Quaternionf(transform.rotation()));
        BodyHandle handle = switch (rigidBody.kind()) {
            case STATIC -> world.addStaticBody(rigidBody.shape(), pose, rigidBody.collisionMask());
            case DYNAMIC -> world.addDynamicBody(rigidBody.shape(), pose, rigidBody.dynamicProperties(), rigidBody.collisionMask());
            case KINEMATIC -> world.addKinematicBody(rigidBody.shape(), pose, rigidBody.collisionMask());
            case AREA -> world.addAreaBody(rigidBody.shape(), pose, rigidBody.collisionMask());
        };
        rigidBody.markRegistered(handle);
    }

    private void pullDynamicTransform(GameObject gameObject, RigidBodyComponent rigidBody) {
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
        RigidBodyPose pose = world.getBodyPose(rigidBody.handle());
        transform.setPosition(pose.position().x(), pose.position().y(), pose.position().z());
        scratchRotation.set(pose.rotation());
        transform.setRotation(scratchRotation);
    }

    public RapierCharacterController attachCharacterController(CharacterControllerComponent component, Transform3D transform) {
        requireWorld();
        RigidBodyPose pose = new RigidBodyPose(new Vector3f(transform.position()), new Quaternionf(transform.rotation()));
        BodyHandle handle = world.addKinematicBody(component.shape(), pose, fr.epistudio.epysia.physics.api.CollisionMask.DEFAULT);
        RapierCharacterController controller = new RapierCharacterController(world);
        ownedControllers.add(controller);
        component.attachNative(handle, controller);
        return controller;
    }

    public Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance) {
        requireWorld();
        return world.raycast(origin, direction, maxDistance, QueryFilter.ALL);
    }

    public PhysicsWorld world() {
        requireWorld();
        return world;
    }

    private void requireWorld() {
        if (world == null) {
            throw new EpysiaException("PhysicsSystem accessed before initialize().");
        }
    }

    @Override
    public void shutdown() {
        for (RapierCharacterController controller : ownedControllers) {
            controller.close();
        }
        ownedControllers.clear();
        if (world != null) {
            world.close();
            world = null;
        }
    }
}
