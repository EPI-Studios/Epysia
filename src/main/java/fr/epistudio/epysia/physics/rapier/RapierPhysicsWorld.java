package fr.epistudio.epysia.physics.rapier;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.physics.api.AreaEvent;
import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CollisionMask;
import fr.epistudio.epysia.physics.api.ContactEvent;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.JointHandle;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.QueryFilter;
import fr.epistudio.epysia.physics.api.RaycastHit;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.ShapeCastHit;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RapierPhysicsWorld implements PhysicsWorld {

    private static final int RAYCAST_RESULT_BYTES = 36;
    private static final int CONTACT_EVENT_STRIDE = 44;
    private static final int AREA_EVENT_STRIDE = 24;
    private static final int OVERLAP_MAX_RESULTS = 256;
    private static final int EVENT_MAX = 1024;

    private final Arena arena = Arena.ofShared();
    private final MemorySegment world;
    private final Map<ShapeDescriptor, Long> primitiveShapeCache = new HashMap<>();
    private final List<Long> trimeshShapeHandles = new ArrayList<>();
    private final MemorySegment transformScratch;
    private final MemorySegment vectorScratch;
    private final MemorySegment raycastScratch;
    private final MemorySegment overlapScratch;
    private final MemorySegment contactEventScratch;
    private final MemorySegment areaEventScratch;
    private boolean closed;

    public RapierPhysicsWorld() {
        this.world = RapierNativeBridge.worldNew();
        this.transformScratch = arena.allocate(ValueLayout.JAVA_FLOAT, 7);
        this.vectorScratch = arena.allocate(ValueLayout.JAVA_FLOAT, 3);
        this.raycastScratch = arena.allocate(RAYCAST_RESULT_BYTES);
        this.overlapScratch = arena.allocate(ValueLayout.JAVA_LONG, OVERLAP_MAX_RESULTS);
        this.contactEventScratch = arena.allocate((long) CONTACT_EVENT_STRIDE * EVENT_MAX);
        this.areaEventScratch = arena.allocate((long) AREA_EVENT_STRIDE * EVENT_MAX);
    }

    public MemorySegment nativeHandle() {
        return world;
    }

    long internShape(ShapeDescriptor descriptor) {
        return shapeHandleFor(descriptor);
    }

    @Override
    public BodyHandle addStaticBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        long bodyId = RapierNativeBridge.bodyAddStatic(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                mask.layer(), mask.mask());
        return new BodyHandle(bodyId);
    }

    @Override
    public BodyHandle addDynamicBody(ShapeDescriptor shape, RigidBodyPose pose, DynamicProperties properties, CollisionMask mask) {
        if (shape instanceof ShapeDescriptor.TriangleMesh) {
            throw new EpysiaException("Triangle mesh shapes cannot back a dynamic body.");
        }
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        long bodyId = RapierNativeBridge.bodyAddDynamic(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                mask.layer(), mask.mask(),
                properties.mass(), properties.gravityScale(),
                properties.linearDamping(), properties.angularDamping(),
                properties.continuousCollisionDetection() ? 1 : 0);
        return new BodyHandle(bodyId);
    }

    @Override
    public BodyHandle addKinematicBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        long bodyId = RapierNativeBridge.bodyAddKinematic(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                mask.layer(), mask.mask());
        return new BodyHandle(bodyId);
    }

    @Override
    public BodyHandle addAreaBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        long bodyId = RapierNativeBridge.bodyAddArea(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                mask.layer(), mask.mask());
        return new BodyHandle(bodyId);
    }

    @Override
    public void removeBody(BodyHandle body) {
        RapierNativeBridge.bodyRemove(world, body.id());
    }

    @Override
    public void setBodyPose(BodyHandle body, RigidBodyPose pose) {
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        RapierNativeBridge.bodySetTransform(world, body.id(),
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    @Override
    public RigidBodyPose getBodyPose(BodyHandle body) {
        RapierNativeBridge.bodyGetTransform(world, body.id(), transformScratch);
        Vector3f position = new Vector3f(
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 2));
        Quaternionf rotation = new Quaternionf(
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 3),
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 4),
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 5),
                transformScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 6));
        return new RigidBodyPose(position, rotation);
    }

    @Override
    public void applyForce(BodyHandle body, Vector3fc force) {
        RapierNativeBridge.bodyApplyForce(world, body.id(), force.x(), force.y(), force.z());
    }

    @Override
    public void applyImpulse(BodyHandle body, Vector3fc impulse) {
        RapierNativeBridge.bodyApplyImpulse(world, body.id(), impulse.x(), impulse.y(), impulse.z());
    }

    @Override
    public void setLinearVelocity(BodyHandle body, Vector3fc velocity) {
        RapierNativeBridge.bodySetLinearVelocity(world, body.id(), velocity.x(), velocity.y(), velocity.z());
    }

    @Override
    public Vector3fc getLinearVelocity(BodyHandle body) {
        RapierNativeBridge.bodyGetLinearVelocity(world, body.id(), vectorScratch);
        return new Vector3f(
                vectorScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 0),
                vectorScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
                vectorScratch.getAtIndex(ValueLayout.JAVA_FLOAT, 2));
    }

    @Override
    public void sleepBody(BodyHandle body) {
        RapierNativeBridge.bodySleep(world, body.id());
    }

    @Override
    public void wakeBody(BodyHandle body) {
        RapierNativeBridge.bodyWake(world, body.id());
    }

    @Override
    public JointHandle addFixedJoint(BodyHandle first, BodyHandle second,
                                     RigidBodyPose localPoseFirst, RigidBodyPose localPoseSecond,
                                     boolean contactsEnabled) {
        Vector3fc positionFirst = localPoseFirst.position();
        Quaternionfc rotationFirst = localPoseFirst.rotation();
        Vector3fc positionSecond = localPoseSecond.position();
        Quaternionfc rotationSecond = localPoseSecond.rotation();
        long jointId = RapierNativeBridge.jointAddFixed(world, first.id(), second.id(),
                positionFirst.x(), positionFirst.y(), positionFirst.z(),
                rotationFirst.x(), rotationFirst.y(), rotationFirst.z(), rotationFirst.w(),
                positionSecond.x(), positionSecond.y(), positionSecond.z(),
                rotationSecond.x(), rotationSecond.y(), rotationSecond.z(), rotationSecond.w(),
                contactsEnabled ? 1 : 0);
        return new JointHandle(jointId);
    }

    @Override
    public JointHandle addSphericalJoint(BodyHandle first, BodyHandle second,
                                         Vector3fc localAnchorFirst, Vector3fc localAnchorSecond,
                                         boolean contactsEnabled) {
        long jointId = RapierNativeBridge.jointAddSpherical(world, first.id(), second.id(),
                localAnchorFirst.x(), localAnchorFirst.y(), localAnchorFirst.z(),
                localAnchorSecond.x(), localAnchorSecond.y(), localAnchorSecond.z(),
                contactsEnabled ? 1 : 0);
        return new JointHandle(jointId);
    }

    @Override
    public void removeJoint(JointHandle joint) {
        RapierNativeBridge.jointRemove(world, joint.id());
    }

    @Override
    public Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance, QueryFilter filter) {
        boolean hit = RapierNativeBridge.queryRaycast(world,
                origin.x(), origin.y(), origin.z(),
                direction.x(), direction.y(), direction.z(),
                maxDistance, filter.mask(), raycastScratch);
        return hit ? Optional.of(decodeRaycastResult(raycastScratch)) : Optional.empty();
    }

    @Override
    public Optional<ShapeCastHit> shapeCast(ShapeDescriptor shape, RigidBodyPose from, Vector3fc direction,
                                            float maxDistance, QueryFilter filter) {
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = from.position();
        Quaternionfc rotation = from.rotation();
        boolean hit = RapierNativeBridge.queryShapeCast(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                direction.x(), direction.y(), direction.z(),
                maxDistance, filter.mask(), raycastScratch);
        return hit ? Optional.of(decodeShapeCastResult(raycastScratch)) : Optional.empty();
    }

    @Override
    public long[] overlap(ShapeDescriptor shape, RigidBodyPose pose, QueryFilter filter) {
        long shapeHandle = shapeHandleFor(shape);
        Vector3fc position = pose.position();
        Quaternionfc rotation = pose.rotation();
        int hitCount = RapierNativeBridge.queryOverlap(world, shapeHandle,
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                filter.mask(), overlapScratch, OVERLAP_MAX_RESULTS);
        long[] results = new long[hitCount];
        for (int i = 0; i < hitCount; i++) {
            results[i] = overlapScratch.getAtIndex(ValueLayout.JAVA_LONG, i);
        }
        return results;
    }

    @Override
    public List<ContactEvent> drainContactEvents() {
        int count = RapierNativeBridge.eventsDrainContacts(world, contactEventScratch, EVENT_MAX);
        List<ContactEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(decodeContactEvent(i));
        }
        return events;
    }

    @Override
    public List<AreaEvent> drainAreaEvents() {
        int count = RapierNativeBridge.eventsDrainAreas(world, areaEventScratch, EVENT_MAX);
        List<AreaEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(decodeAreaEvent(i));
        }
        return events;
    }

    @Override
    public void setGravity(Vector3fc gravity) {
        RapierNativeBridge.worldSetGravity(world, gravity.x(), gravity.y(), gravity.z());
    }

    @Override
    public void step(float stepSeconds) {
        RapierNativeBridge.worldStep(world, stepSeconds);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (long shape : primitiveShapeCache.values()) {
            RapierNativeBridge.shapeDrop(shape);
        }
        primitiveShapeCache.clear();
        for (long shape : trimeshShapeHandles) {
            RapierNativeBridge.shapeDrop(shape);
        }
        trimeshShapeHandles.clear();
        RapierNativeBridge.worldDrop(world);
        arena.close();
    }

    private long shapeHandleFor(ShapeDescriptor descriptor) {
        return switch (descriptor) {
            case ShapeDescriptor.Box box -> internPrimitive(box, key -> RapierNativeBridge.shapeBox(
                    key.halfExtents().x(), key.halfExtents().y(), key.halfExtents().z()));
            case ShapeDescriptor.Sphere sphere -> internPrimitive(sphere, key ->
                    RapierNativeBridge.shapeSphere(key.radius()));
            case ShapeDescriptor.Capsule capsule -> internPrimitive(capsule, key ->
                    RapierNativeBridge.shapeCapsule(key.radius(), key.halfHeight()));
            case ShapeDescriptor.TriangleMesh mesh -> registerTrimesh(uploadTriangleMesh(mesh));
            case ShapeDescriptor.ConvexHull hull -> registerTrimesh(uploadConvexHull(hull));
        };
    }

    private <T extends ShapeDescriptor> long internPrimitive(T key, java.util.function.ToLongFunction<T> creator) {
        Long existing = primitiveShapeCache.get(key);
        if (existing != null) {
            return existing;
        }
        long created = creator.applyAsLong(key);
        primitiveShapeCache.put(key, created);
        return created;
    }

    private long registerTrimesh(long shapeHandle) {
        trimeshShapeHandles.add(shapeHandle);
        return shapeHandle;
    }

    private static long uploadTriangleMesh(ShapeDescriptor.TriangleMesh mesh) {
        try (Arena uploadArena = Arena.ofConfined()) {
            MemorySegment vertices = uploadArena.allocateFrom(ValueLayout.JAVA_FLOAT, mesh.vertices());
            MemorySegment indices = uploadArena.allocateFrom(ValueLayout.JAVA_INT, mesh.indices());
            return RapierNativeBridge.shapeTriangleMesh(vertices, mesh.vertices().length, indices, mesh.indices().length);
        }
    }

    private static long uploadConvexHull(ShapeDescriptor.ConvexHull hull) {
        try (Arena uploadArena = Arena.ofConfined()) {
            MemorySegment vertices = uploadArena.allocateFrom(ValueLayout.JAVA_FLOAT, hull.vertices());
            return RapierNativeBridge.shapeConvexHull(vertices, hull.vertices().length);
        }
    }

    private static RaycastHit decodeRaycastResult(MemorySegment buffer) {
        float pointX = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
        float pointY = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        float pointZ = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
        float normalX = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 3);
        float normalY = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 4);
        float normalZ = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 5);
        float distance = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 6);
        long bodyId = buffer.get(ValueLayout.JAVA_LONG_UNALIGNED, 28L);
        return new RaycastHit(new BodyHandle(bodyId),
                new Vector3f(pointX, pointY, pointZ),
                new Vector3f(normalX, normalY, normalZ),
                distance);
    }

    private static ShapeCastHit decodeShapeCastResult(MemorySegment buffer) {
        float pointX = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
        float pointY = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        float pointZ = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
        float normalX = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 3);
        float normalY = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 4);
        float normalZ = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 5);
        float timeOfImpact = buffer.getAtIndex(ValueLayout.JAVA_FLOAT, 6);
        long bodyId = buffer.get(ValueLayout.JAVA_LONG_UNALIGNED, 28L);
        return new ShapeCastHit(new BodyHandle(bodyId),
                new Vector3f(pointX, pointY, pointZ),
                new Vector3f(normalX, normalY, normalZ),
                timeOfImpact);
    }

    private ContactEvent decodeContactEvent(int eventIndex) {
        long base = (long) eventIndex * CONTACT_EVENT_STRIDE;
        long firstId = contactEventScratch.get(ValueLayout.JAVA_LONG_UNALIGNED, base);
        long secondId = contactEventScratch.get(ValueLayout.JAVA_LONG_UNALIGNED, base + 8);
        float pointX = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 16);
        float pointY = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 20);
        float pointZ = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 24);
        float normalX = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 28);
        float normalY = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 32);
        float normalZ = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 36);
        float impulse = contactEventScratch.get(ValueLayout.JAVA_FLOAT, base + 40);
        return new ContactEvent(new BodyHandle(firstId), new BodyHandle(secondId),
                new Vector3f(pointX, pointY, pointZ),
                new Vector3f(normalX, normalY, normalZ),
                impulse);
    }

    private AreaEvent decodeAreaEvent(int eventIndex) {
        long base = (long) eventIndex * AREA_EVENT_STRIDE;
        long areaId = areaEventScratch.get(ValueLayout.JAVA_LONG_UNALIGNED, base);
        long otherId = areaEventScratch.get(ValueLayout.JAVA_LONG_UNALIGNED, base + 8);
        int enteredFlag = areaEventScratch.get(ValueLayout.JAVA_INT, base + 16);
        return new AreaEvent(new BodyHandle(areaId), new BodyHandle(otherId), enteredFlag != 0);
    }
}
