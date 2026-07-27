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
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dCharacterController;
import fr.epistudio.epysia.physics.box3d.Box3dPhysicsWorld;
import fr.epistudio.epysia.physics.components.CharacterController2D;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.Collider2D;
import fr.epistudio.epysia.physics.components.MeshCollider;
import fr.epistudio.epysia.physics.components.PhysicsMaterial;
import fr.epistudio.epysia.physics.components.RigidBody2D;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.PhysicsEventListener;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class PhysicsSystem implements IPhysicsSystem {

    private static final float DEFAULT_WORLD_FLOOR_Y = -500.0f;
    private static final float RESTING_DISPLACEMENT_SQUARED = 1.0e-8f;

    private final Vector3f defaultGravity = new Vector3f(0.0f, -9.81f, 0.0f);
    private Set<GameObject> residentSnapshot;
    private long residentModificationCount = -1L;
    private float worldFloorY = DEFAULT_WORLD_FLOOR_Y;
    private final Vector3f scratchPosition = new Vector3f();
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchHorizontal = new Vector3f();
    private final Vector3f scratchDisplacement = new Vector3f();
    private final List<Box3dCharacterController> ownedControllers = new ArrayList<>();
    private final Map<Long, GameObject> bodyOwners = new HashMap<>();
    private CollisionLayers collisionLayers = CollisionLayers.allColliding();
    private final Map<BodyPair, ContactEvent> activeContacts = new LinkedHashMap<>();
    private final Set<BodyPair> activeTriggers = new LinkedHashSet<>();
    private final Set<GameObject> warnedMixedPhysics = Collections.newSetFromMap(new IdentityHashMap<>());
    private EngineServices services;
    private Box3dPhysicsWorld world;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
        world = new Box3dPhysicsWorld();
        world.setGravity(defaultGravity);
    }

    public void setGravity(float x, float y, float z) {
        defaultGravity.set(x, y, z);
        if (world != null) {
            world.setGravity(defaultGravity);
        }
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
        warnedMixedPhysics.clear();
        if (world != null) {
            world.close();
        }
        world = new Box3dPhysicsWorld();
        world.setGravity(defaultGravity);
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
        world.step(deltaTimeSeconds);
        pullDynamicTransforms(scene);
        removeBodiesBelowWorldFloor(scene);
        stepCharacterControllers(scene, deltaTimeSeconds);
        dispatchPhysicsEvents();
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

    private void pullDynamicTransforms(Scene scene) {
        for (RigidBodyComponent rigidBody : scene.componentsOf(RigidBodyComponent.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                pullDynamicTransform(owner, rigidBody);
            }
        }
        for (RigidBody2D rigidBody : scene.componentsOf(RigidBody2D.class)) {
            GameObject owner = rigidBody.ownerOrNull();
            if (owner != null) {
                pullDynamicTransform2D(owner, rigidBody);
            }
        }
    }

    private void stepCharacterControllers(Scene scene, float deltaTimeSeconds) {
        for (CharacterControllerComponent controller : scene.componentsOf(CharacterControllerComponent.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                stepCharacterController(owner, controller, deltaTimeSeconds);
            }
        }
        for (CharacterController2D controller : scene.componentsOf(CharacterController2D.class)) {
            GameObject owner = controller.ownerOrNull();
            if (owner != null) {
                stepCharacterController2D(owner, controller, deltaTimeSeconds);
            }
        }
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
        controller.consumeDesiredHorizontalMove(scratchHorizontal);
        float vertical = verticalVelocityFor(controller, deltaTimeSeconds);
        scratchDisplacement.set(scratchHorizontal.x * deltaTimeSeconds, vertical * deltaTimeSeconds, scratchHorizontal.z * deltaTimeSeconds);
        boolean snap = controller.snapToGround() && vertical <= 0.0f;
        Box3dCharacterController.MoveResult result = controller.nativeController()
                .move(controller.bodyHandle(), scratchDisplacement, controller.stepHeight(), snap);
        Vector3fc corrected = result.correctedDisplacement();
        if (corrected.lengthSquared() > RESTING_DISPLACEMENT_SQUARED) {
            Vector3f position = transform.position();
            float newX = position.x + corrected.x();
            float newY = position.y + corrected.y();
            float newZ = position.z + corrected.z();
            transform.setPosition(newX, newY, newZ);
            scratchPosition.set(newX, newY, newZ);
            scratchRotation.set(transform.rotation());
            world.setBodyPose(controller.bodyHandle(), new RigidBodyPose(new Vector3f(scratchPosition), new Quaternionf(scratchRotation)));
        }
        controller.setGrounded(result.grounded());
        controller.setMoveResult(result.groundNormal(), result.clippedDelta(), result.contacts());
        if (controller.applyGravity()) {
            controller.setVerticalVelocity(result.grounded() && vertical < 0.0f ? 0.0f : vertical);
        }
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
        if (rigidBody.kind() == RigidBodyKind.KINEMATIC || rigidBody.kind() == RigidBodyKind.STATIC) {
            Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
            scratchPosition.set(transform.position());
            scratchRotation.set(transform.rotation());
            world.setBodyPose(rigidBody.handle(), new RigidBodyPose(scratchPosition, scratchRotation));
        }
    }

    private void ensureRegistered(GameObject gameObject) {
        Optional<RigidBodyComponent> rigidBodyOptional = gameObject.getComponent(RigidBodyComponent.class);
        List<Collider> colliders = collidersOf(gameObject);
        if (rigidBodyOptional.isEmpty() && colliders.isEmpty()) {
            return;
        }
        if (alreadyRegistered(rigidBodyOptional, colliders)) {
            return;
        }
        registerBody(gameObject, rigidBodyOptional, colliders);
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
        RigidBodyPose pose = new RigidBodyPose(new Vector3f(transform.position()), new Quaternionf(transform.rotation()));
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
        rigidBodyOptional.ifPresent(rigidBody -> rigidBody.markRegistered(handle));
        bodyOwners.put(handle.id(), gameObject);
    }

    private void attachColliders(GameObject gameObject, Transform3D transform, RigidBodyKind kind,
                                 BodyHandle body, List<Collider> colliders) {
        for (Collider collider : colliders) {
            guardDynamicTriangleMesh(gameObject, kind, collider);
            PhysicsMaterial material = collider.resolvedMaterial();
            ShapeDescriptor shape = scaledShape(collider.shape(), transform);
            int group = collisionLayers.groupFor(collider.collisionLayer());
            int mask = collisionLayers.maskFor(collider.collisionLayer());
            world.addCollider(body, shape, collider.offset(), collider.isTrigger(), material, group, mask);
        }
    }

    private static ShapeDescriptor scaledShape(ShapeDescriptor shape, Transform3D transform) {
        Vector3fc scale = transform.scale();
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
        for (Collider2D collider : colliders) {
            int group = collisionLayers.groupFor(collider.collisionLayer());
            int mask = collisionLayers.maskFor(collider.collisionLayer());
            for (Collider2D.ShapePlacement placement : collider.shapePlacements()) {
                ShapeDescriptor shape = scaledShape2D(placement.shape(), transform);
                Vector2f offset = placement.offset();
                world.addCollider(body, shape, new Vector3f(offset.x, offset.y, 0.0f),
                        collider.isTrigger(), PhysicsMaterial.DEFAULT, group, mask);
            }
        }
    }

    private static ShapeDescriptor scaledShape2D(ShapeDescriptor shape, Transform2D transform) {
        float scaleX = Math.abs(transform.scale().x);
        float scaleY = Math.abs(transform.scale().y);
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
        return new RigidBodyPose(
                new Vector3f(transform.position().x + offset.x, transform.position().y + offset.y, 0.0f),
                new Quaternionf().rotationZ(transform.rotationRadians()));
    }

    private static RigidBodyPose planePoseOf(Transform2D transform) {
        Vector3f position = new Vector3f(transform.position().x, transform.position().y, 0.0f);
        Quaternionf rotation = new Quaternionf().rotationZ(transform.rotationRadians());
        return new RigidBodyPose(position, rotation);
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

    private void pullDynamicTransform2D(GameObject gameObject, RigidBody2D rigidBody) {
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
            return;
        }
        if (!world.isBodyAwake(rigidBody.handle())) {
            return;
        }
        Transform2D transform = gameObject.getComponent(Transform2D.class).orElseThrow();
        RigidBodyPose pose = world.getBodyPose(rigidBody.handle());
        transform.setPosition(pose.position().x(), pose.position().y());
        transform.setRotationRadians(planeAngleOf(pose.rotation()));
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
        scratchDisplacement.set(horizontal * deltaTimeSeconds, vertical * deltaTimeSeconds, 0.0f);
        boolean snap = controller.snapToGround() && vertical <= 0.0f;
        Box3dCharacterController.MoveResult result = controller.nativeController()
                .move(controller.bodyHandle(), scratchDisplacement, controller.stepHeight(), snap);
        applyControllerDisplacement2D(controller, transform, result);
        controller.setGrounded(result.grounded());
        controller.setVerticalVelocity(result.grounded() && vertical < 0.0f ? 0.0f : vertical);
    }

    private void applyControllerDisplacement2D(CharacterController2D controller, Transform2D transform,
                                               Box3dCharacterController.MoveResult result) {
        Vector3fc corrected = result.correctedDisplacement();
        if (corrected.lengthSquared() <= RESTING_DISPLACEMENT_SQUARED) {
            return;
        }
        float newX = transform.position().x + corrected.x();
        float newY = transform.position().y + corrected.y();
        transform.setPosition(newX, newY);
        Vector2f offset = controller.capsuleOffset();
        world.setBodyPose(controller.bodyHandle(), new RigidBodyPose(
                new Vector3f(newX + offset.x, newY + offset.y, 0.0f), new Quaternionf()));
    }

    private static float verticalVelocityFor2D(CharacterController2D controller, float deltaTimeSeconds) {
        float jumpSpeed = controller.consumeJumpRequest();
        if (jumpSpeed > 0.0f && controller.grounded()) {
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

    private void pullDynamicTransform(GameObject gameObject, RigidBodyComponent rigidBody) {
        if (rigidBody.kind() != RigidBodyKind.DYNAMIC || !rigidBody.handle().isValid()) {
            return;
        }
        if (!world.isBodyAwake(rigidBody.handle())) {
            return;
        }
        Transform3D transform = gameObject.getComponent(Transform3D.class).orElseThrow();
        RigidBodyPose pose = world.getBodyPose(rigidBody.handle());
        transform.setPosition(pose.position().x(), pose.position().y(), pose.position().z());
        scratchRotation.set(pose.rotation());
        transform.setRotation(scratchRotation);
    }

    private void dispatchPhysicsEvents() {
        for (ContactEvent event : world.drainContactEvents()) {
            handleContactEvent(event);
        }
        for (AreaEvent event : world.drainAreaEvents()) {
            handleAreaEvent(event);
        }
        fireStayEvents();
    }

    private void handleContactEvent(ContactEvent event) {
        BodyPair pair = BodyPair.of(event.first().id(), event.second().id());
        if (event.started()) {
            activeContacts.put(pair, event);
        } else {
            activeContacts.remove(pair);
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
        RigidBodyPose pose = new RigidBodyPose(new Vector3f(transform.position()), new Quaternionf(transform.rotation()));
        BodyHandle handle = world.addKinematicBody(capsule, pose, CollisionMask.DEFAULT);
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
}
