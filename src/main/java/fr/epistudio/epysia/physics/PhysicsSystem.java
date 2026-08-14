package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.physics.api.AreaEvent;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CharacterContact;
import fr.epistudio.epysia.physics.api.CollisionLayers;
import fr.epistudio.epysia.physics.api.CollisionMask;
import fr.epistudio.epysia.physics.api.ContactEvent;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.IPhysicsSystem;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.QueryFilter;
import fr.epistudio.epysia.physics.api.RaycastHit;
import fr.epistudio.epysia.physics.api.RaycastHit2D;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.ShapeCastHit;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;
import fr.epistudio.epysia.physics.box3d.Box3dPhysicsWorld;
import fr.epistudio.epysia.physics.components.CharacterController2D;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.Collider2D;
import fr.epistudio.epysia.physics.components.JointComponent;
import fr.epistudio.epysia.physics.components.MeshCollider;
import fr.epistudio.epysia.physics.components.PhysicsMaterial;
import fr.epistudio.epysia.physics.components.RigidBody2D;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.PhysicsEventListener;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import fr.epistudio.epysia.debug.DebugDraw;
import fr.epistudio.epysia.physics.api.PhysicsDebugLines;

public final class PhysicsSystem implements IPhysicsSystem {
    private static final float DEFAULT_WORLD_FLOOR_Y = -500.0f;
    private static final float GROUND_CONTACT_MINIMUM_UPWARD = 0.5f;
    private static final float RESTING_DISPLACEMENT_SQUARED = 1.0e-8f;
    private static final int MINIMUM_STEP_HERTZ = 10;
    private static final int MAXIMUM_STEP_HERTZ = 240;
    private static final int MAXIMUM_CATCHUP_STEPS = 5;
    private static final int DEFAULT_MAXIMUM_HITS = 32;
    private static final float DEFAULT_STEP_SECONDS = 1.0f / 60.0f;

    private final Vector3f defaultGravity = new Vector3f(0.0f, -9.81f, 0.0f);
    private Set<GameObject> residentSnapshot;
    private long residentModificationCount = -1L;
    private float worldFloorY = DEFAULT_WORLD_FLOOR_Y;
    private final Vector3f scratchPosition = new Vector3f();
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchHorizontal = new Vector3f();
    private final Vector3f scratchDisplacement = new Vector3f();
    private final Map<Long, Matrix3x2f> platformPoses = new HashMap<>();
    private final Set<Long> platformsThisStep = new HashSet<>();
    private final Matrix3x2f scratchPlatformPose = new Matrix3x2f();
    private final Vector2f scratchCarry = new Vector2f();
    private final List<Box3dCharacterController> ownedControllers = new ArrayList<>();
    private final Map<Long, GameObject> bodyOwners = new HashMap<>();
    private CollisionLayers collisionLayers = CollisionLayers.allColliding();
    private final Map<BodyPair, ContactEvent> activeContacts = new LinkedHashMap<>();
    private final Set<BodyPair> activeTriggers = new LinkedHashSet<>();
    private final Set<Long> passableBodies = new HashSet<>();
    private final Map<Long, Set<Long>> characterContacts = new HashMap<>();
    private final List<ContactEvent> pendingCharacterEvents = new ArrayList<>();
    private final Set<GameObject> warnedMixedPhysics = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Long, RigidBodyPose> previousPoses = new HashMap<>();
    private final Map<Long, RigidBodyPose> currentPoses = new HashMap<>();
    private float fixedStepSeconds = DEFAULT_STEP_SECONDS;
    private float stepAccumulator;
    private BodyHandle worldAnchor = BodyHandle.NONE;
    private EngineServices services;
    private Box3dPhysicsWorld world;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
        this.debugDraw = services == null ? DebugDraw.detached() : services.debug();
        world = new Box3dPhysicsWorld();
        world.setGravity(defaultGravity);
    }

    public void setGravity(float x, float y, float z) {
        defaultGravity.set(x, y, z);
        if (world != null) {
            world.setGravity(defaultGravity);
        }
    }

    private final List<FixedStepListener> fixedStepListeners = new ArrayList<>();
    private DebugDraw debugDraw;
    private boolean debugDrawEnabled;

    public void setFixedTimestepHertz(int hertz) {
        fixedStepSeconds = 1.0f / Math.clamp(hertz, MINIMUM_STEP_HERTZ, MAXIMUM_STEP_HERTZ);
        stepAccumulator = 0.0f;
    }

    public float fixedStepSeconds() {
        return fixedStepSeconds;
    }

    public void setCollisionLayers(CollisionLayers layers) {
        this.collisionLayers = layers;
    }

    public void resetForPlaySession() {
        for (Box3dCharacterController controller : ownedControllers) {
            controller.close();
        }
        ownedControllers.clear();
        bodyOwners.clear();
        activeContacts.clear();
        activeTriggers.clear();
        passableBodies.clear();
        characterContacts.clear();
        pendingCharacterEvents.clear();
        warnedMixedPhysics.clear();
        previousPoses.clear();
        currentPoses.clear();
        stepAccumulator = 0.0f;
        worldAnchor = BodyHandle.NONE;
        if (world != null) {
            world.close();
        }
        world = new Box3dPhysicsWorld();
        world.setGravity(defaultGravity);
    }

    public Optional<BodyHandle> bodyOf(GameObject gameObject) {
        if (gameObject == null) {
            return Optional.empty();
        }
        return firstValidHandle(
                gameObject.getComponent(CharacterController2D.class).map(CharacterController2D::bodyHandle),
                gameObject.getComponent(CharacterControllerComponent.class).map(CharacterControllerComponent::bodyHandle),
                gameObject.getComponent(RigidBody2D.class).map(RigidBody2D::handle),
                gameObject.getComponent(RigidBodyComponent.class).map(RigidBodyComponent::handle));
    }

    @SafeVarargs
    private static Optional<BodyHandle> firstValidHandle(Optional<BodyHandle>... candidates) {
        for (Optional<BodyHandle> candidate : candidates) {
            if (candidate.isPresent() && candidate.get().isValid()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<GameObject> ownerOf(BodyHandle body) {
        return Optional.ofNullable(bodyOwners.get(body.id()));
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (world == null) {
            return;
        }
        registerSceneBodies(scene);
        syncKinematicBodiesToPhysics(scene);
        ensureCharacterControllers(scene);
        destroyOrphanBodies(scene);
        refreshPassableBodies();
        registerSceneJoints(scene);
        advanceFixedSteps(scene, deltaTimeSeconds);
        writeInterpolatedTransforms(scene);
        removeBodiesBelowWorldFloor(scene);
        drawDebugIntoOverlay();
        dispatchPhysicsEvents();
    }

    private void registerSceneJoints(Scene scene) {
        for (JointComponent joint : scene.componentsOf(JointComponent.class)) {
            if (joint.requiresRebuild()) {
                releaseJoint(joint);
            }
            if (!joint.isRegistered()) {
                tryRegisterJoint(joint);
            }
        }
    }

    private void releaseJoint(JointComponent joint) {
        if (joint.handle().isValid()) {
            world.removeJoint(joint.handle());
        }
        joint.clearRegistered();
    }

    private void tryRegisterJoint(JointComponent joint) {
        GameObject owner = joint.ownerOrNull();
        if (owner == null) {
            return;
        }
        Optional<BodyHandle> ownerBody = bodyOf(owner);
        Optional<BodyHandle> otherBody = joint.connectedBody().isEmpty()
                ? Optional.of(worldAnchorBody()) : bodyOf(joint.connectedBody().get());
        if (ownerBody.isEmpty() || otherBody.isEmpty()) {
            return;
        }
        Vector3f ownerAnchor = worldAnchorOf(owner, joint.anchor());
        Vector3f otherAnchor = joint.connectedBody()
                .map(other -> worldAnchorOf(other, new Vector3f()))
                .orElseGet(() -> new Vector3f(ownerAnchor));
        joint.markRegistered(world.addJoint(ownerBody.get(), otherBody.get(),
                joint.describe(ownerAnchor, otherAnchor)));
    }

    private static Vector3f worldAnchorOf(GameObject gameObject, Vector3f localAnchor) {
        return gameObject.getComponent(Transform3D.class)
                .map(transform -> transform.worldMatrix().transformPosition(new Vector3f(localAnchor)))
                .orElseGet(() -> new Vector3f(localAnchor));
    }

    private BodyHandle worldAnchorBody() {
        if (!worldAnchor.isValid()) {
            worldAnchor = world.createBody(RigidBodyKind.STATIC,
                    new RigidBodyPose(new Vector3f(), new Quaternionf()),
                    DynamicProperties.defaults(), false);
        }
        return worldAnchor;
    }

    public void addFixedStepListener(FixedStepListener listener) {
        if (listener != null && !fixedStepListeners.contains(listener)) {
            fixedStepListeners.add(listener);
        }
    }

    public void removeFixedStepListener(FixedStepListener listener) {
        fixedStepListeners.remove(listener);
    }

    private void notifyFixedStep(Scene scene) {
        for (FixedStepListener listener : List.copyOf(fixedStepListeners)) {
            listener.onFixedStep(scene, fixedStepSeconds);
        }
    }

    private void advanceFixedSteps(Scene scene, float deltaTimeSeconds) {
        stepAccumulator += Math.max(0.0f, deltaTimeSeconds);
        int steps = 0;
        while (stepAccumulator >= fixedStepSeconds && steps < MAXIMUM_CATCHUP_STEPS) {
            notifyFixedStep(scene);
            capturePreviousPoses(scene);
            world.step(fixedStepSeconds);
            capturePoses(scene, currentPoses);
            stepCharacterControllers(scene, fixedStepSeconds);
            stepAccumulator -= fixedStepSeconds;
            steps++;
        }
        if (stepAccumulator >= fixedStepSeconds) {
            stepAccumulator = 0.0f;
        }
        if (steps == 0 && currentPoses.isEmpty()) {
            capturePoses(scene, currentPoses);
            previousPoses.putAll(currentPoses);
        }
    }

    public void stepOnce(Scene scene, float deltaTimeSeconds) {
        capturePreviousPoses(scene);
        world.step(deltaTimeSeconds);
        capturePoses(scene, currentPoses);
        stepCharacterControllers(scene, deltaTimeSeconds);
    }

    private void capturePreviousPoses(Scene scene) {
        previousPoses.clear();
        if (currentPoses.isEmpty()) {
            capturePoses(scene, previousPoses);
            return;
        }
        previousPoses.putAll(currentPoses);
    }

    private void capturePoses(Scene scene, Map<Long, RigidBodyPose> destination) {
        destination.clear();
        for (RigidBodyComponent rigidBody : scene.componentsOf(RigidBodyComponent.class)) {
            if (rigidBody.kind() == RigidBodyKind.DYNAMIC && rigidBody.handle().isValid()) {
                destination.put(rigidBody.handle().id(), world.getBodyPose(rigidBody.handle()));
            }
        }
        for (RigidBody2D rigidBody : scene.componentsOf(RigidBody2D.class)) {
            if (rigidBody.kind() == RigidBodyKind.DYNAMIC && rigidBody.handle().isValid()) {
                destination.put(rigidBody.handle().id(), world.getBodyPose(rigidBody.handle()));
            }
        }
    }

    private float interpolationAlpha() {
        return Math.clamp(stepAccumulator / fixedStepSeconds, 0.0f, 1.0f);
    }

    private void registerSceneBodies(Scene scene) {
        for (GameObject gameObject : physicsCandidates(scene)) {
            ensureRegistered(gameObject);
            ensureRegistered2D(gameObject);
        }
    }

    private Collection<GameObject> physicsCandidates(Scene scene) {
        Set<GameObject> candidates = new LinkedHashSet<>();
        collectOwners(scene, RigidBodyComponent.class, candidates);
        collectOwners(scene, Collider.class, candidates);
        collectOwners(scene, RigidBody2D.class, candidates);
        collectOwners(scene, Collider2D.class, candidates);
        return candidates;
    }

    private void collectOwners(Scene scene, Class<? extends IComponent> type, Set<GameObject> owners) {
        for (IComponent component : scene.componentsOf(type)) {
            GameObject owner = component.ownerOrNull();
            if (owner != null) {
                owners.add(owner);
            }
        }
    }

    private void syncKinematicBodiesToPhysics(Scene scene) {
        for (RigidBodyComponent rigidBody : scene.componentsOf(RigidBodyComponent.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                syncRigidBodyToPhysics(owner, rigidBody);
            }
        }
        for (RigidBody2D rigidBody : scene.componentsOf(RigidBody2D.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                syncRigidBody2DToPhysics(owner, rigidBody);
            }
        }
        for (CharacterControllerComponent controller : scene.componentsOf(CharacterControllerComponent.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                syncCharacterToTransform(owner);
            }
        }
    }

    private void ensureCharacterControllers(Scene scene) {
        for (CharacterControllerComponent controller : scene.componentsOf(CharacterControllerComponent.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                ensureCharacterController(owner, controller);
            }
        }
        for (CharacterController2D controller : scene.componentsOf(CharacterController2D.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                ensureCharacterController2D(owner, controller);
            }
        }
    }

    private void writeInterpolatedTransforms(Scene scene) {
        float alpha = interpolationAlpha();
        for (RigidBodyComponent rigidBody : scene.componentsOf(RigidBodyComponent.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                pullDynamicTransform(owner, rigidBody, alpha);
            }
        }
        for (RigidBody2D rigidBody : scene.componentsOf(RigidBody2D.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                pullDynamicTransform2D(owner, rigidBody, alpha);
            }
        }
    }

    private RigidBodyPose displayedPose(BodyHandle handle, boolean interpolate, float alpha) {
        RigidBodyPose current = currentPoses.get(handle.id());
        if (current == null) {
            return world.getBodyPose(handle);
        }
        RigidBodyPose previous = previousPoses.get(handle.id());
        if (!interpolate || previous == null) {
            return current;
        }
        Vector3f position = new Vector3f(previous.position()).lerp(current.position(), alpha);
        Quaternionf rotation = new Quaternionf(previous.rotation()).slerp(current.rotation(), alpha);
        return new RigidBodyPose(position, rotation);
    }

    private void stepCharacterControllers(Scene scene, float deltaTimeSeconds) {
        for (CharacterControllerComponent controller : scene.componentsOf(CharacterControllerComponent.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null && controller.simulated()) {
                stepCharacterController(owner, controller, deltaTimeSeconds);
            }
        }
        platformsThisStep.clear();
        for (CharacterController2D controller : scene.componentsOf(CharacterController2D.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                stepCharacterController2D(owner, controller, deltaTimeSeconds);
            }
        }
        platformPoses.keySet().retainAll(platformsThisStep);
    }

    private void ensureCharacterController(GameObject gameObject, CharacterControllerComponent controller) {
        if (controller.nativeController() != null) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("CharacterControllerComponent requires Transform3D on " + gameObject.name()));
        attachCharacterController(controller, transform);
        bodyOwners.put(controller.bodyHandle().id(), gameObject);
    }

    private void stepCharacterController(GameObject gameObject, CharacterControllerComponent controller, float deltaTimeSeconds) {
        if (controller.nativeController() == null || !controller.bodyHandle().isValid()) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
        if (applyPendingTeleport(controller, transform)) {
            return;
        }
        controller.consumeDesiredHorizontalMove(scratchHorizontal);
        float vertical = verticalVelocityFor(controller, deltaTimeSeconds);
        scratchDisplacement.set(scratchHorizontal.x * deltaTimeSeconds, vertical * deltaTimeSeconds, scratchHorizontal.z * deltaTimeSeconds);
        boolean snap = controller.snapToGround() && vertical <= 0.0f;
        controller.nativeController().setBodyFilter(this::blocksCharacter);
        Box3dCharacterController.MoveResult result = controller.nativeController()
                .move(controller.bodyHandle(), scratchDisplacement, controller.stepHeight(), snap);
        applyControllerDisplacement(transform, result.correctedDisplacement());
        writeControllerPose(controller, transform);
        controller.setGrounded(result.grounded());
        controller.setMoveResult(result.groundNormal(), result.clippedDelta(), result.contacts());
        recordCharacterContacts(controller.bodyHandle(), result.contacts(), deltaTimeSeconds);
        if (controller.applyGravity()) {
            controller.setVerticalVelocity(result.grounded() && vertical < 0.0f ? 0.0f : vertical);
        }
    }

    private void applyControllerDisplacement(Transform3D transform, Vector3fc corrected) {
        if (corrected.lengthSquared() <= RESTING_DISPLACEMENT_SQUARED) {
            return;
        }
        transform.worldPosition(scratchPosition);
        transform.setWorldPosition(scratchPosition.x + corrected.x(),
                scratchPosition.y + corrected.y(),
                scratchPosition.z + corrected.z());
    }

    private void writeControllerPose(CharacterControllerComponent controller, Transform3D transform) {
        world.setBodyPose(controller.bodyHandle(), worldPoseOf(transform));
    }

    private boolean blocksCharacter(long bodyKey) {
        return !passableBodies.contains(bodyKey);
    }

    public void syncCharacterToTransform(GameObject gameObject) {
        CharacterControllerComponent controller =
                gameObject.getComponentOrNull(CharacterControllerComponent.class);
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (controller == null || transform == null || !controller.bodyHandle().isValid()) {
            return;
        }
        writeControllerPose(controller, transform);
    }

    public void stepCharacter(GameObject gameObject, float deltaTimeSeconds) {
        CharacterControllerComponent controller =
                gameObject.getComponentOrNull(CharacterControllerComponent.class);
        if (controller != null) {
            stepCharacterController(gameObject, controller, deltaTimeSeconds);
        }
    }

    private boolean applyPendingTeleport(CharacterControllerComponent controller, Transform3D transform) {
        if (!controller.consumeTeleport(scratchPosition)) {
            return false;
        }
        transform.setWorldPosition(scratchPosition.x, scratchPosition.y, scratchPosition.z);
        writeControllerPose(controller, transform);
        transform.resetInterpolation();
        controller.consumeDesiredHorizontalMove(scratchHorizontal);
        return true;
    }

    private static float verticalVelocityFor(CharacterControllerComponent controller, float deltaTimeSeconds) {
        if (!controller.applyGravity()) {
            controller.consumeJumpRequest();
            return controller.verticalVelocity();
        }
        if (controller.consumeJumpRequest() && controller.grounded()) {
            return controller.jumpSpeed();
        }
        return controller.verticalVelocity() + controller.gravity() * deltaTimeSeconds;
    }

    private void syncRigidBodyToPhysics(GameObject gameObject, RigidBodyComponent rigidBody) {
        if (!rigidBody.isRegistered() || !rigidBody.handle().isValid()) {
            return;
        }
        if (rigidBody.kind() != RigidBodyKind.KINEMATIC && rigidBody.kind() != RigidBodyKind.STATIC) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
        transform.worldPosition(scratchPosition);
        transform.worldRotation(scratchRotation);
        if (rigidBody.kind() == RigidBodyKind.KINEMATIC
                && rigidBody.dynamicProperties().continuousCollisionDetection()) {
            sweepKinematicToTarget(rigidBody);
            return;
        }
        world.setBodyPose(rigidBody.handle(), new RigidBodyPose(scratchPosition, scratchRotation));
    }

    private void sweepKinematicToTarget(RigidBodyComponent rigidBody) {
        RigidBodyPose current = world.getBodyPose(rigidBody.handle());
        Vector3f delta = new Vector3f(scratchPosition).sub(current.position());
        world.setLinearVelocity(rigidBody.handle(), delta.mul(1.0f / fixedStepSeconds));
        world.setBodyPose(rigidBody.handle(),
                new RigidBodyPose(current.position(), new Quaternionf(scratchRotation)));
    }

    private void ensureRegistered(GameObject gameObject) {
        Optional<RigidBodyComponent> rigidBodyOptional = gameObject.getComponent(RigidBodyComponent.class);
        List<Collider> colliders = collidersOf(gameObject);
        if (rigidBodyOptional.isEmpty() && colliders.isEmpty()) {
            return;
        }
        if (colliders.stream().anyMatch(Collider::requiresRebuild)) {
            releaseBody(gameObject, rigidBodyOptional, colliders);
        }
        if (alreadyRegistered(rigidBodyOptional, colliders)) {
            return;
        }
        registerBody(gameObject, rigidBodyOptional, colliders);
    }

    private void releaseBody(GameObject gameObject, Optional<RigidBodyComponent> rigidBodyOptional,
                             List<Collider> colliders) {
        bodyOwners.entrySet().removeIf(entry -> {
            if (entry.getValue() != gameObject) {
                return false;
            }
            world.removeBody(new BodyHandle(entry.getKey()));
            return true;
        });
        colliders.forEach(Collider::clearRegistered);
        rigidBodyOptional.ifPresent(RigidBodyComponent::clearRegistered);
    }

    private boolean alreadyRegistered(Optional<RigidBodyComponent> rigidBodyOptional, List<Collider> colliders) {
        if (rigidBodyOptional.isPresent()) {
            return rigidBodyOptional.get().isRegistered();
        }
        return colliders.stream().allMatch(Collider::isRegistered);
    }

    private void registerBody(GameObject gameObject, Optional<RigidBodyComponent> rigidBodyOptional,
                              List<Collider> colliders) {
        Transform3D transform = gameObject.getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("Physics body requires Transform3D on " + gameObject.name()));
        RigidBodyKind kind = rigidBodyOptional.map(RigidBodyComponent::kind).orElse(RigidBodyKind.STATIC);
        RigidBodyPose pose = worldPoseOf(transform);
        DynamicProperties properties = rigidBodyOptional.map(RigidBodyComponent::dynamicProperties)
                .orElse(DynamicProperties.defaults());
        boolean canSleep = rigidBodyOptional.map(RigidBodyComponent::canSleep).orElse(true);
        BodyHandle handle = world.createBody(kind, pose, properties, canSleep);
        if (!handle.isValid() && !colliders.isEmpty()) {
            throw new EpysiaException("Collider on '" + gameObject.name()
                    + "' has no RigidBody and no implicit static body was created");
        }
        attachColliders(gameObject, transform, kind, handle, colliders);
        colliders.forEach(Collider::markRegistered);
        rigidBodyOptional.ifPresent(rigidBody -> activate(rigidBody, handle));
        bodyOwners.put(handle.id(), gameObject);
    }

    private void activate(RigidBodyComponent rigidBody, BodyHandle handle) {
        rigidBody.attachWorld(world);
        rigidBody.markRegistered(handle);
        world.setSleepEnabled(handle, rigidBody.canSleep());
        world.setSleepThreshold(handle, rigidBody.sleepThreshold());
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC) {
            return;
        }
        world.setMotionLocks(handle, rigidBody.motionLocks());
        if (rigidBody.overrideCenterOfMass()) {
            world.setCenterOfMass(handle, world.getMass(handle), rigidBody.centerOfMass());
        }
    }

    private void attachColliders(GameObject gameObject, Transform3D transform, RigidBodyKind kind,
                                 BodyHandle body, List<Collider> colliders) {
        Vector3fc scale = transform.worldScale(new Vector3f());
        for (Collider collider : colliders) {
            guardDynamicTriangleMesh(gameObject, kind, collider);
            PhysicsMaterial material = collider.resolvedMaterial();
            ShapeDescriptor shape = scaledShape(collider.shape(), scale);
            int group = collisionLayers.groupFor(collider.collisionLayer());
            int mask = collisionLayers.maskFor(collider.collisionLayer());
            Vector3f offset = scaledOffset(collider.offset(), scale);
            world.addCollider(body, shape, offset, collider.isTrigger(), material, group, mask);
        }
    }

    private static RigidBodyPose worldPoseOf(Transform3D transform) {
        return new RigidBodyPose(transform.worldPosition(new Vector3f()),
                transform.worldRotation(new Quaternionf()));
    }

    private static Vector3f scaledOffset(Vector3fc offset, Vector3fc scale) {
        return new Vector3f(offset.x() * Math.abs(scale.x()),
                offset.y() * Math.abs(scale.y()),
                offset.z() * Math.abs(scale.z()));
    }

    private static ShapeDescriptor scaledShape(ShapeDescriptor shape, Vector3fc scale) {
        float scaleX = Math.abs(scale.x());
        float scaleY = Math.abs(scale.y());
        float scaleZ = Math.abs(scale.z());
        return switch (shape) {
            case ShapeDescriptor.Box box -> new ShapeDescriptor.Box(new Vector3f(
                    box.halfExtents().x() * scaleX,
                    box.halfExtents().y() * scaleY,
                    box.halfExtents().z() * scaleZ));
            case ShapeDescriptor.Sphere sphere -> new ShapeDescriptor.Sphere(
                    sphere.radius() * Math.max(scaleX, Math.max(scaleY, scaleZ)));
            case ShapeDescriptor.Capsule capsule -> new ShapeDescriptor.Capsule(
                    capsule.radius() * Math.max(scaleX, scaleZ),
                    capsule.halfHeight() * scaleY);
            default -> shape;
        };
    }

    private static void guardDynamicTriangleMesh(GameObject gameObject, RigidBodyKind kind, Collider collider) {
        if (kind != RigidBodyKind.DYNAMIC || !(collider instanceof MeshCollider meshCollider)) {
            return;
        }
        if (!meshCollider.convex()) {
            throw new EpysiaException("MeshCollider on '" + gameObject.name()
                    + "' is a triangle mesh and cannot back a DYNAMIC body; mark it convex or use a primitive collider.");
        }
    }

    private void ensureRegistered2D(GameObject gameObject) {
        Optional<RigidBody2D> rigidBodyOptional = gameObject.getComponent(RigidBody2D.class);
        List<Collider2D> colliders = colliders2DOf(gameObject);
        if (rigidBodyOptional.isEmpty() && colliders.isEmpty()) {
            return;
        }
        if (hasVolumetricPhysics(gameObject)) {
            warnMixedPhysics(gameObject);
            return;
        }
        if (colliders.stream().anyMatch(Collider2D::requiresRebuild)) {
            releasePlaneBody(gameObject, rigidBodyOptional, colliders);
        }
        if (alreadyRegistered2D(rigidBodyOptional, colliders)) {
            return;
        }
        registerPlaneBody(gameObject, rigidBodyOptional, colliders);
    }

    private void releasePlaneBody(GameObject gameObject, Optional<RigidBody2D> rigidBodyOptional,
                                  List<Collider2D> colliders) {
        bodyOwners.entrySet().removeIf(entry -> {
            if (entry.getValue() != gameObject) {
                return false;
            }
            world.removeBody(new BodyHandle(entry.getKey()));
            return true;
        });
        colliders.forEach(Collider2D::clearRegistered);
        rigidBodyOptional.ifPresent(RigidBody2D::clearRegistered);
    }

    private boolean hasVolumetricPhysics(GameObject gameObject) {
        return gameObject.getComponent(RigidBodyComponent.class).isPresent()
                || !collidersOf(gameObject).isEmpty();
    }

    private void warnMixedPhysics(GameObject gameObject) {
        if (warnedMixedPhysics.add(gameObject)) {
            services.logger().warn("[PhysicsSystem] " + gameObject.name()
                    + " mixes 2D and 3D physics components; keeping the 3D body and ignoring the 2D components.");
        }
    }

    private static boolean alreadyRegistered2D(Optional<RigidBody2D> rigidBodyOptional, List<Collider2D> colliders) {
        if (rigidBodyOptional.isPresent()) {
            return rigidBodyOptional.get().isRegistered();
        }
        return colliders.stream().allMatch(Collider2D::isRegistered);
    }

    private void registerPlaneBody(GameObject gameObject, Optional<RigidBody2D> rigidBodyOptional,
                                   List<Collider2D> colliders) {
        Transform2D transform = gameObject.getComponent(Transform2D.class)
                .orElseThrow(() -> new EpysiaException("2D physics body requires Transform2D on " + gameObject.name()));
        RigidBodyKind kind = rigidBodyOptional.map(RigidBody2D::kind).orElse(RigidBodyKind.STATIC);
        DynamicProperties properties = rigidBodyOptional.map(RigidBody2D::dynamicProperties)
                .orElse(DynamicProperties.defaults());
        BodyHandle handle = world.createBody(kind, planePoseOf(transform), properties, true);
        attachColliders2D(handle, transform, colliders);
        if (kind == RigidBodyKind.DYNAMIC) {
            world.lockToPlane(handle, rigidBodyOptional.map(RigidBody2D::fixedRotation).orElse(false));
        }
        colliders.forEach(Collider2D::markRegistered);
        rigidBodyOptional.ifPresent(rigidBody -> rigidBody.markRegistered(handle));
        bodyOwners.put(handle.id(), gameObject);
    }

    private void attachColliders2D(BodyHandle body, Transform2D transform, List<Collider2D> colliders) {
        Vector2f scale = transform.worldScale(new Vector2f());
        for (Collider2D collider : colliders) {
            int group = collisionLayers.groupFor(collider.collisionLayer());
            int mask = collisionLayers.maskFor(collider.collisionLayer());
            for (Collider2D.ShapePlacement placement : collider.shapePlacements()) {
                ShapeDescriptor shape = scaledShape2D(placement.shape(), scale);
                Vector2f offset = placement.offset();
                world.addCollider(body, shape,
                        new Vector3f(offset.x * scale.x, offset.y * scale.y, 0.0f),
                        collider.isTrigger(), PhysicsMaterial.DEFAULT, group, mask);
            }
        }
    }

    private static ShapeDescriptor scaledShape2D(ShapeDescriptor shape, Vector2f scale) {
        float scaleX = scale.x;
        float scaleY = scale.y;
        return switch (shape) {
            case ShapeDescriptor.Box box -> new ShapeDescriptor.Box(new Vector3f(
                    box.halfExtents().x() * scaleX,
                    box.halfExtents().y() * scaleY,
                    box.halfExtents().z()));
            case ShapeDescriptor.Sphere sphere -> new ShapeDescriptor.Sphere(
                    sphere.radius() * Math.max(scaleX, scaleY));
            case ShapeDescriptor.Capsule capsule -> new ShapeDescriptor.Capsule(
                    capsule.radius() * Math.max(scaleX, scaleY), capsule.halfHeight() * scaleY);
            case ShapeDescriptor.ConvexHull hull -> new ShapeDescriptor.ConvexHull(
                    scaledVertices(hull.vertices(), scaleX, scaleY));
            default -> shape;
        };
    }

    private static float[] scaledVertices(float[] vertices, float scaleX, float scaleY) {
        float[] scaled = new float[vertices.length];
        for (int index = 0; index + 2 < vertices.length; index += 3) {
            scaled[index] = vertices[index] * scaleX;
            scaled[index + 1] = vertices[index + 1] * scaleY;
            scaled[index + 2] = vertices[index + 2];
        }
        return scaled;
    }

    private static RigidBodyPose controllerPoseOf(Transform2D transform, CharacterController2D controller) {
        Vector2f offset = controller.capsuleOffset();
        Vector2f worldPosition = transform.worldOrigin(new Vector2f());
        return new RigidBodyPose(
                new Vector3f(worldPosition.x + offset.x, worldPosition.y + offset.y, 0.0f),
                new Quaternionf().rotationZ(transform.worldRotationRadians()));
    }

    private static RigidBodyPose planePoseOf(Transform2D transform) {
        Vector2f worldPosition = transform.worldOrigin(new Vector2f());
        Quaternionf rotation = new Quaternionf().rotationZ(transform.worldRotationRadians());
        return new RigidBodyPose(new Vector3f(worldPosition.x, worldPosition.y, 0.0f), rotation);
    }

    private static float planeAngleOf(Quaternionfc rotation) {
        return (float) (2.0 * Math.atan2(rotation.z(), rotation.w()));
    }

    private void syncRigidBody2DToPhysics(GameObject gameObject, RigidBody2D rigidBody) {
        if (!rigidBody.isRegistered() || !rigidBody.handle().isValid()) {
            return;
        }
        if (rigidBody.kind() == RigidBodyKind.KINEMATIC || rigidBody.kind() == RigidBodyKind.STATIC) {
            Transform2D transform = gameObject.getComponent(Transform2D.class).orElseThrow();
            world.setBodyPose(rigidBody.handle(), planePoseOf(transform));
        }
    }

    private void pullDynamicTransform2D(GameObject gameObject, RigidBody2D rigidBody, float alpha) {
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
            return;
        }
        if (!world.isBodyAwake(rigidBody.handle())) {
            return;
        }
        Transform2D transform = gameObject.getComponent(Transform2D.class).orElseThrow();
        RigidBodyPose pose = displayedPose(rigidBody.handle(), rigidBody.interpolate(), alpha);
        transform.setWorldOrigin(pose.position().x(), pose.position().y());
        transform.setWorldRotationRadians(planeAngleOf(pose.rotation()));
    }

    private void ensureCharacterController2D(GameObject gameObject, CharacterController2D controller) {
        if (controller.hasNativeController()) {
            return;
        }
        Transform2D transform = gameObject.getComponent(Transform2D.class)
                .orElseThrow(() -> new EpysiaException("CharacterController2D requires Transform2D on " + gameObject.name()));
        ShapeDescriptor.Capsule capsule = controller.shape();
        BodyHandle handle = world.addKinematicBody(capsule,
                controllerPoseOf(transform, controller), CollisionMask.DEFAULT);
        Box3dCharacterController nativeController = new Box3dCharacterController(world, capsule.radius(),
                capsule.halfHeight(), controller.maxSlopeDegrees());
        ownedControllers.add(nativeController);
        controller.attachNative(handle, nativeController);
        bodyOwners.put(handle.id(), gameObject);
    }

    private void stepCharacterController2D(GameObject gameObject, CharacterController2D controller, float deltaTimeSeconds) {
        if (!controller.hasNativeController() || !controller.bodyHandle().isValid()) {
            return;
        }
        Transform2D transform = gameObject.getComponent(Transform2D.class).orElseThrow();
        float horizontal = controller.consumeDesiredMove();
        float vertical = verticalVelocityFor2D(controller, deltaTimeSeconds);
        carryWithPlatform(controller, transform, scratchCarry);
        scratchDisplacement.set(horizontal * deltaTimeSeconds + scratchCarry.x,
                vertical * deltaTimeSeconds + scratchCarry.y, 0.0f);
        boolean snap = controller.snapToGround() && vertical <= 0.0f;
        controller.nativeController().setBodyFilter(
                bodyKey -> !isPassable(bodyKey, horizontal, vertical));
        Box3dCharacterController.MoveResult result = controller.nativeController()
                .move(controller.bodyHandle(), scratchDisplacement, controller.stepHeight(), snap);
        applyControllerDisplacement2D(controller, transform, result);
        controller.setGrounded(result.grounded());
        controller.setContacts(result.contacts());
        controller.setVerticalVelocity(result.grounded() && vertical < 0.0f ? 0.0f : vertical);
        rememberGroundPlatform(controller, result);
    }

    private void carryWithPlatform(CharacterController2D controller, Transform2D transform, Vector2f carry) {
        carry.set(0.0f, 0.0f);
        if (!controller.carriedByPlatforms() || !controller.groundBody().isValid()) {
            return;
        }
        Matrix3x2f previous = platformPoses.get(controller.groundBody().id());
        GameObject platform = bodyOwners.get(controller.groundBody().id());
        if (previous == null || platform == null) {
            return;
        }
        Transform2D platformTransform = platform.getComponentOrNull(Transform2D.class);
        if (platformTransform == null) {
            return;
        }
        Vector2f worldPosition = transform.worldOrigin(new Vector2f());
        PlatformCarry.delta(previous, platformTransform.worldMatrix(),
                worldPosition.x, worldPosition.y, scratchPlatformPose, carry);
    }

    private void rememberGroundPlatform(CharacterController2D controller,
                                        Box3dCharacterController.MoveResult result) {
        BodyHandle ground = result.grounded() ? groundBodyOf(result) : BodyHandle.NONE;
        controller.setGroundBody(ground);
        if (!ground.isValid()) {
            return;
        }
        GameObject platform = bodyOwners.get(ground.id());
        Transform2D platformTransform = platform == null
                ? null : platform.getComponentOrNull(Transform2D.class);
        if (platformTransform == null) {
            return;
        }
        platformsThisStep.add(ground.id());
        platformPoses.computeIfAbsent(ground.id(), key -> new Matrix3x2f())
                .set(platformTransform.worldMatrix());
    }

    private static BodyHandle groundBodyOf(Box3dCharacterController.MoveResult result) {
        BodyHandle best = BodyHandle.NONE;
        float bestUpward = GROUND_CONTACT_MINIMUM_UPWARD;
        for (CharacterContact contact : result.contacts()) {
            float upward = contact.normal().y();
            if (upward > bestUpward) {
                bestUpward = upward;
                best = contact.body();
            }
        }
        return best;
    }

    private boolean isPassable(long bodyKey, float horizontal, float vertical) {
        if (passableBodies.contains(bodyKey)) {
            return true;
        }
        GameObject owner = bodyOwners.get(bodyKey);
        if (owner == null) {
            return false;
        }
        for (IComponent component : owner.components()) {
            if (component instanceof Collider2D collider && collider.passableAlong(horizontal, vertical)) {
                return true;
            }
        }
        return false;
    }

    private void applyControllerDisplacement2D(CharacterController2D controller, Transform2D transform,
                                               Box3dCharacterController.MoveResult result) {
        Vector3fc corrected = result.correctedDisplacement();
        if (corrected.lengthSquared() <= RESTING_DISPLACEMENT_SQUARED) {
            return;
        }
        Vector2f worldPosition = transform.worldOrigin(new Vector2f());
        float newX = worldPosition.x + corrected.x();
        float newY = worldPosition.y + corrected.y();
        transform.setWorldOrigin(newX, newY);
        Vector2f offset = controller.capsuleOffset();
        world.setBodyPose(controller.bodyHandle(), new RigidBodyPose(
                new Vector3f(newX + offset.x, newY + offset.y, 0.0f), new Quaternionf()));
    }

    private static float verticalVelocityFor2D(CharacterController2D controller, float deltaTimeSeconds) {
        float jumpSpeed = controller.consumeJumpRequest();
        if (jumpSpeed != 0.0f) {
            return jumpSpeed;
        }
        return controller.verticalVelocity() + controller.gravity() * deltaTimeSeconds;
    }

    private static List<Collider2D> colliders2DOf(GameObject gameObject) {
        List<Collider2D> colliders = new ArrayList<>();
        for (IComponent component : gameObject.components()) {
            if (component instanceof Collider2D collider) {
                colliders.add(collider);
            }
        }
        return colliders;
    }

    private void refreshPassableBodies() {
        passableBodies.clear();
        for (Map.Entry<Long, GameObject> entry : bodyOwners.entrySet()) {
            if (onlyTriggers(entry.getValue())) {
                passableBodies.add(entry.getKey());
            }
        }
    }

    private static boolean onlyTriggers(GameObject gameObject) {
        boolean sawCollider = false;
        for (IComponent component : gameObject.components()) {
            if (component instanceof Collider collider) {
                if (!collider.isTrigger()) {
                    return false;
                }
                sawCollider = true;
            } else if (component instanceof Collider2D collider) {
                if (!collider.isTrigger()) {
                    return false;
                }
                sawCollider = true;
            }
        }
        return sawCollider;
    }

    private static List<Collider> collidersOf(GameObject gameObject) {
        List<Collider> colliders = new ArrayList<>();
        for (IComponent component : gameObject.components()) {
            if (component instanceof Collider collider) {
                colliders.add(collider);
            }
        }
        return colliders;
    }

    public void setWorldFloor(float worldFloorY) {
        this.worldFloorY = worldFloorY;
    }

    private void destroyOrphanBodies(Scene scene) {
        if (bodyOwners.isEmpty()) {
            return;
        }
        Set<GameObject> resident = residentObjects(scene);
        Iterator<Map.Entry<Long, GameObject>> entries = bodyOwners.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, GameObject> entry = entries.next();
            if (!resident.contains(entry.getValue())) {
                world.removeBody(new BodyHandle(entry.getKey()));
                entries.remove();
            }
        }
    }

    private Set<GameObject> residentObjects(Scene scene) {
        if (residentSnapshot == null || residentModificationCount != scene.modificationCount()) {
            residentSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
            residentSnapshot.addAll(scene.gameObjects());
            residentModificationCount = scene.modificationCount();
        }
        return residentSnapshot;
    }

    private void removeBodiesBelowWorldFloor(Scene scene) {
        for (RigidBodyComponent rigidBody : List.copyOf(scene.componentsOf(RigidBodyComponent.class))) {
            GameObject gameObject = rigidBody.ownerOrNull();
            if (gameObject == null || rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
                continue;
            }
            float bodyY = world.getBodyPose(rigidBody.handle()).position().y();
            if (bodyY < worldFloorY) {
                services.logger().warn("[PhysicsSystem] Removing " + gameObject.name()
                        + ", fell below the world floor (y=" + bodyY + ")");
                scene.removeGameObject(gameObject);
            }
        }
    }

    private void pullDynamicTransform(GameObject gameObject, RigidBodyComponent rigidBody, float alpha) {
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
            return;
        }
        if (!world.isBodyAwake(rigidBody.handle())) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
        RigidBodyPose pose = displayedPose(rigidBody.handle(), rigidBody.interpolate(), alpha);
        transform.setWorldPosition(pose.position().x(), pose.position().y(), pose.position().z());
        transform.setWorldRotation(pose.rotation());
    }

    private void dispatchPhysicsEvents() {
        for (ContactEvent event : world.drainContactEvents()) {
            handleContactEvent(event);
        }
        for (ContactEvent event : pendingCharacterEvents) {
            handleContactEvent(event);
        }
        pendingCharacterEvents.clear();
        for (AreaEvent event : world.drainAreaEvents()) {
            handleAreaEvent(event);
        }
        fireStayEvents();
    }

    private void recordCharacterContacts(BodyHandle body, List<CharacterContact> contacts, float deltaTimeSeconds) {
        Set<Long> previous = characterContacts.getOrDefault(body.id(), Set.of());
        Set<Long> current = new LinkedHashSet<>();
        for (CharacterContact contact : contacts) {
            if (current.add(contact.body().id()) && !previous.contains(contact.body().id())) {
                pendingCharacterEvents.add(new ContactEvent(body, contact.body(), contact.point(),
                        contact.normal(), approachSpeedAlong(contact.normal(), deltaTimeSeconds), true));
            }
        }
        for (long departed : previous) {
            if (!current.contains(departed)) {
                pendingCharacterEvents.add(new ContactEvent(body, new BodyHandle(departed),
                        new Vector3f(), new Vector3f(), 0.0f, false));
            }
        }
        characterContacts.put(body.id(), current);
    }

    private float approachSpeedAlong(Vector3fc normal, float deltaTimeSeconds) {
        float along = -scratchDisplacement.dot(normal);
        if (along <= 0.0f || deltaTimeSeconds <= 0.0f) {
            return 0.0f;
        }
        return along / deltaTimeSeconds;
    }

    private void handleContactEvent(ContactEvent event) {
        BodyPair pair = BodyPair.of(event.first().id(), event.second().id());
        if (event.started() && activeContacts.put(pair, event) != null) {
            return;
        }
        if (!event.started() && activeContacts.remove(pair) == null) {
            return;
        }
        GameObject first = bodyOwners.get(event.first().id());
        GameObject second = bodyOwners.get(event.second().id());
        dispatchContact(first, second, event, event.started());
        dispatchContact(second, first, event, event.started());
    }

    private void handleAreaEvent(AreaEvent event) {
        BodyPair pair = BodyPair.of(event.area().id(), event.other().id());
        if (event.entered()) {
            activeTriggers.add(pair);
        } else {
            activeTriggers.remove(pair);
        }
        GameObject area = bodyOwners.get(event.area().id());
        GameObject other = bodyOwners.get(event.other().id());
        dispatchTrigger(area, other, event.entered());
        dispatchTrigger(other, area, event.entered());
    }

    private void fireStayEvents() {
        for (ContactEvent event : activeContacts.values()) {
            GameObject first = bodyOwners.get(event.first().id());
            GameObject second = bodyOwners.get(event.second().id());
            dispatchContactStay(first, second, event);
            dispatchContactStay(second, first, event);
        }
        for (BodyPair pair : activeTriggers) {
            GameObject low = bodyOwners.get(pair.low());
            GameObject high = bodyOwners.get(pair.high());
            dispatchTriggerStay(low, high);
            dispatchTriggerStay(high, low);
        }
    }

    private void dispatchContact(GameObject target, GameObject counterpart,
                                 ContactEvent event, boolean started) {
        if (target == null) {
            return;
        }
        dispatchToBehaviours(target, behaviour -> {
            try {
                if (started) {
                    behaviour.onCollision(counterpart, event.point(), event.normal(), event.approachSpeed());
                } else {
                    behaviour.onCollisionExit(counterpart);
                }
            } catch (RuntimeException error) {
                logScriptError("onCollision", error);
            }
        });
    }

    private void dispatchContactStay(GameObject target, GameObject counterpart,
                                     ContactEvent event) {
        if (target == null) {
            return;
        }
        dispatchToBehaviours(target, behaviour -> {
            try {
                behaviour.onCollisionStay(counterpart, event.point(), event.normal(), event.approachSpeed());
            } catch (RuntimeException error) {
                logScriptError("onCollisionStay", error);
            }
        });
    }

    private void dispatchTriggerStay(GameObject target, GameObject counterpart) {
        if (target == null) {
            return;
        }
        dispatchToBehaviours(target, behaviour -> {
            try {
                behaviour.onTriggerStay(counterpart);
            } catch (RuntimeException error) {
                logScriptError("onTriggerStay", error);
            }
        });
    }

    private record BodyPair(long low, long high) {
        static BodyPair of(long first, long second) {
            return first <= second ? new BodyPair(first, second) : new BodyPair(second, first);
        }
    }

    private void dispatchTrigger(GameObject target, GameObject counterpart, boolean entered) {
        if (target == null) {
            return;
        }
        dispatchToBehaviours(target, behaviour -> {
            try {
                if (entered) {
                    behaviour.onTriggerEnter(counterpart);
                } else {
                    behaviour.onTriggerExit(counterpart);
                }
            } catch (RuntimeException error) {
                logScriptError("onTrigger", error);
            }
        });
    }

    private void dispatchToBehaviours(GameObject gameObject, Consumer<PhysicsEventListener> action) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof PhysicsEventListener listener) {
                action.accept(listener);
            }
        }
    }

    private void logScriptError(String hook, RuntimeException error) {
        if (services != null) {
            services.logger().error("[PhysicsSystem] " + hook + " threw in a physics event listener", error);
        }
    }

    public Box3dCharacterController attachCharacterController(CharacterControllerComponent component, Transform3D transform) {
        requireWorld();
        if (!(component.shape() instanceof ShapeDescriptor.Capsule capsule)) {
            throw new EpysiaException("Character controller requires a capsule shape.");
        }
        BodyHandle handle = world.addKinematicBody(capsule, worldPoseOf(transform), CollisionMask.DEFAULT);
        Box3dCharacterController controller = new Box3dCharacterController(world, capsule.radius(),
                capsule.halfHeight(), component.maxSlopeDegrees());
        ownedControllers.add(controller);
        component.attachNative(handle, controller);
        return controller;
    }

    public Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance) {
        requireWorld();
        return world.raycast(origin, direction, maxDistance, QueryFilter.ALL);
    }

    public List<RaycastHit> raycastAll(Vector3fc origin, Vector3fc direction, float maxDistance) {
        return raycastAll(origin, direction, maxDistance, QueryFilter.ALL, DEFAULT_MAXIMUM_HITS);
    }

    public List<RaycastHit> raycastAll(Vector3fc origin, Vector3fc direction, float maxDistance,
                                       QueryFilter filter, int maximumHits) {
        requireWorld();
        return world.raycastAll(origin, direction, maxDistance, filter, Math.max(1, maximumHits));
    }

    public Optional<ShapeCastHit> shapeCast(ShapeDescriptor shape, RigidBodyPose from, Vector3fc direction,
                                            float maxDistance) {
        return shapeCast(shape, from, direction, maxDistance, QueryFilter.ALL);
    }

    public Optional<ShapeCastHit> shapeCast(ShapeDescriptor shape, RigidBodyPose from, Vector3fc direction,
                                            float maxDistance, QueryFilter filter) {
        requireWorld();
        return world.shapeCast(shape, from, direction, maxDistance, filter);
    }

    public List<GameObject> overlap(ShapeDescriptor shape, RigidBodyPose pose) {
        return overlap(shape, pose, QueryFilter.ALL);
    }

    public List<GameObject> overlap(ShapeDescriptor shape, RigidBodyPose pose, QueryFilter filter) {
        requireWorld();
        List<GameObject> found = new ArrayList<>();
        for (long key : world.overlap(shape, pose, filter)) {
            ownerOf(new BodyHandle(key)).ifPresent(found::add);
        }
        return found;
    }

    public Optional<RaycastHit2D> raycast2D(Vector2fc origin, Vector2fc direction, float maxDistance) {
        return raycast2D(origin, direction, maxDistance, QueryFilter.ALL);
    }

    public Optional<RaycastHit2D> raycast2D(Vector2fc origin, Vector2fc direction, float maxDistance, QueryFilter filter) {
        requireWorld();
        return world.raycast(new Vector3f(origin.x(), origin.y(), 0.0f),
                        new Vector3f(direction.x(), direction.y(), 0.0f), maxDistance, filter)
                .map(PhysicsSystem::toPlaneHit);
    }

    private static RaycastHit2D toPlaneHit(RaycastHit hit) {
        return new RaycastHit2D(hit.body(),
                new Vector2f(hit.point().x(), hit.point().y()),
                new Vector2f(hit.normal().x(), hit.normal().y()),
                hit.distance());
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
        for (Box3dCharacterController controller : ownedControllers) {
            controller.close();
        }
        ownedControllers.clear();
        if (world != null) {
            world.close();
            world = null;
        }
    }

    public void drawDebug(PhysicsDebugLines lines) {
        if (world != null) {
            world.drawDebug(lines);
        }
    }

    public boolean debugDrawEnabled() {
        return debugDrawEnabled;
    }

    public PhysicsSystem setDebugDrawEnabled(boolean value) {
        debugDrawEnabled = value;
        return this;
    }

    private void drawDebugIntoOverlay() {
        if (!debugDrawEnabled || debugDraw == null || !debugDraw.enabled()) {
            return;
        }
        drawDebug((startX, startY, startZ, endX, endY, endZ, color) ->
                debugDraw.line(startX, startY, startZ, endX, endY, endZ, color, DebugDraw.ONE_FRAME));
    }
}
