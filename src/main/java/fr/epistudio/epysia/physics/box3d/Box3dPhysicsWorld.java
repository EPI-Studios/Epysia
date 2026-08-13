package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.*;
import fr.epistudio.epysia.physics.api.*;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.physics.components.PhysicsMaterial;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Box3dPhysicsWorld implements PhysicsWorld {
    private static final com.meekdev.box3d.B3DebugFlags DEBUG_FLAGS =
            com.meekdev.box3d.B3DebugFlags.shapesAndJoints();

    private static final int SUBSTEP_COUNT = 4;
    private static final float DEFAULT_FRICTION = 0.5f;
    private static final float QUERY_SKIP_EPSILON = 1.0e-4f;

    private final B3World world = B3World.create(new Vec3(0.0, -9.81, 0.0));
    private final Map<Long, Box3dBodyState> bodies = new HashMap<>();
    private final Map<Long, B3Joint> joints = new HashMap<>();
    private final List<Runnable> bakedShapeReleases = new ArrayList<>();
    private final List<ContactEvent> pendingContactEvents = new ArrayList<>();
    private final List<AreaEvent> pendingAreaEvents = new ArrayList<>();
    private long nextJointId = 1L;
    private boolean closed;

    B3World nativeWorld() {
        return world;
    }

    B3Body body(BodyHandle handle) {
        return stateOf(handle).body;
    }

    @Override
    public BodyHandle addStaticBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        Box3dBodyState state = createState(B3BodyType.STATIC, pose, false);
        attachShape(state, shape, new Vector3f(), configFor(mask), false);
        return handleOf(state);
    }

    @Override
    public BodyHandle addDynamicBody(ShapeDescriptor shape, RigidBodyPose pose, DynamicProperties properties, CollisionMask mask) {
        if (shape instanceof ShapeDescriptor.TriangleMesh) {
            throw new EpysiaException("Triangle mesh shapes cannot back a dynamic body.");
        }
        Box3dBodyState state = createState(B3BodyType.DYNAMIC, pose, false);
        state.desiredMass = properties.mass();
        attachShape(state, shape, new Vector3f(), configFor(mask), false);
        applyDynamicProperties(state.body, properties, true);
        return handleOf(state);
    }

    @Override
    public BodyHandle addKinematicBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        Box3dBodyState state = createState(B3BodyType.KINEMATIC, pose, false);
        attachShape(state, shape, new Vector3f(), configFor(mask), false);
        return handleOf(state);
    }

    @Override
    public BodyHandle addAreaBody(ShapeDescriptor shape, RigidBodyPose pose, CollisionMask mask) {
        Box3dBodyState state = createState(B3BodyType.STATIC, pose, true);
        attachShape(state, shape, new Vector3f(), configFor(mask), true);
        return handleOf(state);
    }

    public BodyHandle createBody(RigidBodyKind kind, RigidBodyPose pose, DynamicProperties properties, boolean canSleep) {
        Box3dBodyState state = createState(bodyTypeOf(kind), pose, kind == RigidBodyKind.AREA);
        if (kind == RigidBodyKind.DYNAMIC) {
            state.desiredMass = properties.mass();
            applyDynamicProperties(state.body, properties, canSleep);
        }
        return handleOf(state);
    }

    public void addCollider(BodyHandle body, ShapeDescriptor shape, Vector3fc offset, boolean isTrigger,
                            PhysicsMaterial material, int group, int mask) {
        Box3dBodyState state = stateOf(body);
        B3ShapeConfig config = B3ShapeConfig.defaults()
                .withFriction(material.dynamicFriction())
                .withRestitution(material.restitution())
                .withFilter(Integer.toUnsignedLong(group), Integer.toUnsignedLong(mask));
        attachShape(state, shape, offset, config, isTrigger);
    }

    @Override
    public void removeBody(BodyHandle body) {
        Box3dBodyState state = bodies.remove(body.id());
        if (state != null) {
            state.body.destroy();
        }
    }

    @Override
    public void setBodyPose(BodyHandle body, RigidBodyPose pose) {
        body(body).setTransform(Box3dShapeAttacher.toVec(pose.position()), toQuat(pose.rotation()));
    }

    @Override
    public RigidBodyPose getBodyPose(BodyHandle body) {
        B3Body nativeBody = body(body);
        return new RigidBodyPose(toVector(nativeBody.position()), toQuaternion(nativeBody.rotation()));
    }

    @Override
    public void applyForce(BodyHandle body, Vector3fc force) {
        body(body).applyForceToCenter(Box3dShapeAttacher.toVec(force));
    }

    @Override
    public void applyImpulse(BodyHandle body, Vector3fc impulse) {
        B3Body nativeBody = body(body);
        nativeBody.applyImpulseAt(Box3dShapeAttacher.toVec(impulse), nativeBody.worldCenterOfMass());
    }

    @Override
    public void applyImpulseAt(BodyHandle body, Vector3fc impulse, Vector3fc worldPoint) {
        body(body).applyImpulseAt(Box3dShapeAttacher.toVec(impulse), Box3dShapeAttacher.toVec(worldPoint));
    }

    @Override
    public void applyTorque(BodyHandle body, Vector3fc torque) {
        body(body).applyTorque(Box3dShapeAttacher.toVec(torque));
    }

    @Override
    public void applyAngularImpulse(BodyHandle body, Vector3fc impulse) {
        body(body).applyAngularImpulse(Box3dShapeAttacher.toVec(impulse));
    }

    @Override
    public void setLinearVelocity(BodyHandle body, Vector3fc velocity) {
        body(body).setLinearVelocity(Box3dShapeAttacher.toVec(velocity));
    }

    @Override
    public SleepState getSleepState(BodyHandle handle) {
        B3Body body = body(handle);
        if (body == null) {
            return SleepState.AWAKE;
        }
        return new SleepState(body.isAwake(), body.sleepTime());
    }

    @Override
    public void setSleepState(BodyHandle handle, SleepState state) {
        B3Body body = body(handle);
        if (body == null) {
            return;
        }
        body.setSleepTime(state.sleepTimeSeconds());
        body.setAwake(state.awake());
    }

    @Override
    public ContactImpulseSnapshot saveContactImpulses(BodyHandle handle) {
        B3Body body = body(handle);
        if (body == null) {
            return ContactImpulseSnapshot.EMPTY;
        }
        return new Box3dContactImpulses(body.saveContactImpulses());
    }

    @Override
    public Vector3fc getLinearVelocity(BodyHandle body) {
        return toVector(body(body).linearVelocity());
    }

    @Override
    public void setAngularVelocity(BodyHandle body, Vector3fc velocity) {
        body(body).setAngularVelocity(Box3dShapeAttacher.toVec(velocity));
    }

    @Override
    public Vector3fc getAngularVelocity(BodyHandle body) {
        return toVector(body(body).angularVelocity());
    }

    @Override
    public void setMotionLocks(BodyHandle body, MotionLocks locks) {
        body(body).setMotionLocks(locks.linearX(), locks.linearY(), locks.linearZ(),
                locks.angularX(), locks.angularY(), locks.angularZ());
    }

    @Override
    public void lockToPlane(BodyHandle body, boolean freezeRotation) {
        setMotionLocks(body, MotionLocks.planeXY(freezeRotation));
    }

    @Override
    public void setBodyKind(BodyHandle body, RigidBodyKind kind) {
        body(body).setType(bodyTypeOf(kind));
    }

    @Override
    public void setCenterOfMass(BodyHandle body, float mass, Vector3fc localCenter) {
        body(body).setMass(mass, Box3dShapeAttacher.toVec(localCenter));
    }

    @Override
    public Vector3fc getCenterOfMass(BodyHandle body) {
        return toVector(body(body).worldCenterOfMass());
    }

    @Override
    public float getMass(BodyHandle body) {
        return body(body).mass();
    }

    @Override
    public void setSleepEnabled(BodyHandle body, boolean enabled) {
        body(body).enableSleep(enabled);
    }

    @Override
    public void setSleepThreshold(BodyHandle body, float speed) {
        body(body).setSleepThreshold(speed);
    }

    @Override
    public void sleepBody(BodyHandle body) {
        body(body).setAwake(false);
    }

    @Override
    public void wakeBody(BodyHandle body) {
        body(body).setAwake(true);
    }

    @Override
    public boolean isBodyAwake(BodyHandle body) {
        return body(body).isAwake();
    }

    @Override
    public JointHandle addJoint(BodyHandle first, BodyHandle second, JointDescriptor descriptor) {
        B3Body firstBody = body(first);
        B3Body secondBody = body(second);
        return switch (descriptor) {
            case JointDescriptor.Hinge hinge -> registerJoint(createHinge(firstBody, secondBody, hinge));
            case JointDescriptor.Ball ball -> registerJoint(createBall(firstBody, secondBody, ball));
            case JointDescriptor.Weld weld -> registerJoint(createWeld(firstBody, secondBody, weld));
            case JointDescriptor.Distance distance ->
                    registerJoint(createDistance(firstBody, secondBody, distance));
            case JointDescriptor.Slider slider -> registerJoint(createSlider(firstBody, secondBody, slider));
            case JointDescriptor.CollisionFilter ignored ->
                    registerJoint(world.createFilterJoint(firstBody, secondBody));
        };
    }

    private B3RevoluteJoint createHinge(B3Body first, B3Body second, JointDescriptor.Hinge hinge) {
        B3RevoluteJoint joint = world.createRevoluteJoint(first, second,
                Box3dShapeAttacher.toVec(hinge.worldPivot()), Box3dShapeAttacher.toVec(hinge.worldAxis()));
        joint.enableLimit(hinge.angleLimits().enabled());
        joint.setLimits(hinge.angleLimits().lower(), hinge.angleLimits().upper());
        joint.enableMotor(hinge.motorEnabled());
        joint.setMotor(hinge.motorSpeed(), hinge.maxMotorTorque());
        return joint;
    }

    private B3SphericalJoint createBall(B3Body first, B3Body second, JointDescriptor.Ball ball) {
        B3SphericalJoint joint = world.createSphericalJoint(first, second,
                Box3dShapeAttacher.toVec(ball.worldPivot()));
        joint.enableConeLimit(ball.coneLimitEnabled());
        joint.setConeLimit(ball.coneLimitRadians());
        joint.enableTwistLimit(ball.twistLimits().enabled());
        joint.setTwistLimits(ball.twistLimits().lower(), ball.twistLimits().upper());
        joint.enableSpring(ball.springEnabled());
        joint.setSpring(ball.springHertz(), ball.springDampingRatio());
        return joint;
    }

    private B3WeldJoint createWeld(B3Body first, B3Body second, JointDescriptor.Weld weld) {
        B3WeldJoint joint = world.createWeldJoint(first, second, Box3dShapeAttacher.toVec(weld.worldPivot()));
        joint.setLinearSpring(weld.linearHertz(), weld.linearDampingRatio());
        joint.setAngularSpring(weld.angularHertz(), weld.angularDampingRatio());
        return joint;
    }

    private B3DistanceJoint createDistance(B3Body first, B3Body second, JointDescriptor.Distance distance) {
        B3DistanceJoint joint = world.createDistanceJoint(first, second,
                Box3dShapeAttacher.toVec(distance.worldAnchorFirst()),
                Box3dShapeAttacher.toVec(distance.worldAnchorSecond()), distance.length());
        joint.enableLimit(distance.lengthLimits().enabled());
        joint.setLengthRange(distance.lengthLimits().lower(), distance.lengthLimits().upper());
        joint.enableSpring(distance.springEnabled());
        joint.setSpring(distance.springHertz(), distance.springDampingRatio());
        return joint;
    }

    private B3PrismaticJoint createSlider(B3Body first, B3Body second, JointDescriptor.Slider slider) {
        B3PrismaticJoint joint = world.createPrismaticJoint(first, second,
                Box3dShapeAttacher.toVec(slider.worldPivot()), Box3dShapeAttacher.toVec(slider.worldAxis()));
        joint.enableLimit(slider.translationLimits().enabled());
        joint.setLimits(slider.translationLimits().lower(), slider.translationLimits().upper());
        joint.enableMotor(slider.motorEnabled());
        joint.setMotor(slider.motorSpeed(), slider.maxMotorForce());
        return joint;
    }

    @Override
    public void removeJoint(JointHandle joint) {
        B3Joint nativeJoint = joints.remove(joint.id());
        if (nativeJoint != null) {
            nativeJoint.destroy();
        }
    }

    @Override
    public List<RaycastHit> raycastAll(Vector3fc origin, Vector3fc direction, float maxDistance,
                                       QueryFilter filter, int maximumHits) {
        List<RaycastHit> hits = new ArrayList<>();
        Set<Long> alreadyHit = new HashSet<>();
        Vector3f normalized = new Vector3f(direction).normalize();
        Vec3 start = new Vec3(origin.x(), origin.y(), origin.z());
        float traveled = 0.0f;
        int attempts = 0;
        int attemptBudget = bodies.size() * 2 + 2;
        while (hits.size() < maximumHits && traveled < maxDistance && attempts < attemptBudget) {
            attempts++;
            float remaining = maxDistance - traveled;
            B3World.RayHit hit = world.castRayClosest(start, scaled(normalized, remaining), queryFilterOf(filter));
            if (!hit.hit() || hit.body() == null) {
                break;
            }
            float hitDistance = (float) (hit.fraction() * remaining);
            long key = hit.body().key();
            if (key != filter.excludedBodyId() && !isAreaBody(key) && alreadyHit.add(key)) {
                hits.add(new RaycastHit(new BodyHandle(key), toVector(hit.point()),
                        toVector(hit.normal()), traveled + hitDistance));
            }
            float advance = hitDistance + QUERY_SKIP_EPSILON;
            start = start.add(normalized.x * advance, normalized.y * advance, normalized.z * advance);
            traveled += advance;
        }
        hits.sort(Comparator.comparingDouble(RaycastHit::distance));
        return hits;
    }

    @Override
    public Optional<RaycastHit> raycast(Vector3fc origin, Vector3fc direction, float maxDistance, QueryFilter filter) {
        Vector3f normalized = new Vector3f(direction).normalize();
        Vec3 start = new Vec3(origin.x(), origin.y(), origin.z());
        float traveled = 0.0f;
        int maxAttempts = bodies.size() + 1;
        for (int attempt = 0; attempt < maxAttempts && traveled < maxDistance; attempt++) {
            float remaining = maxDistance - traveled;
            B3World.RayHit hit = world.castRayClosest(start, scaled(normalized, remaining), queryFilterOf(filter));
            if (!hit.hit() || hit.body() == null) {
                return Optional.empty();
            }
            float hitDistance = (float) (hit.fraction() * remaining);
            if (hit.body().key() != filter.excludedBodyId() && !isAreaBody(hit.body().key())) {
                return Optional.of(new RaycastHit(new BodyHandle(hit.body().key()),
                        toVector(hit.point()), toVector(hit.normal()), traveled + hitDistance));
            }
            float advance = hitDistance + QUERY_SKIP_EPSILON;
            start = start.add(normalized.x * advance, normalized.y * advance, normalized.z * advance);
            traveled += advance;
        }
        return Optional.empty();
    }

    @Override
    public Optional<ShapeCastHit> shapeCast(ShapeDescriptor shape, RigidBodyPose from, Vector3fc direction,
                                            float maxDistance, QueryFilter filter) {
        float radius = Box3dShapeAttacher.boundingRadius(shape);
        Vector3f normalized = new Vector3f(direction).normalize();
        B3World.ShapeHit hit = world.castSphereClosest(Box3dShapeAttacher.toVec(from.position()), radius,
                scaled(normalized, maxDistance), queryFilterOf(filter));
        if (!hit.hit() || hit.body() == null || hit.body().key() == filter.excludedBodyId()
                || isAreaBody(hit.body().key())) {
            return Optional.empty();
        }
        return Optional.of(new ShapeCastHit(new BodyHandle(hit.body().key()),
                toVector(hit.point()), toVector(hit.normal()), (float) hit.fraction()));
    }

    @Override
    public long[] overlap(ShapeDescriptor shape, RigidBodyPose pose, QueryFilter filter) {
        float radius = Box3dShapeAttacher.boundingRadius(shape);
        Vector3fc position = pose.position();
        Vec3 minimum = new Vec3(position.x() - radius, position.y() - radius, position.z() - radius);
        Vec3 maximum = new Vec3(position.x() + radius, position.y() + radius, position.z() + radius);
        return world.overlapAABB(minimum, maximum).stream()
                .map(B3Body::key)
                .filter(key -> key != filter.excludedBodyId())
                .filter(key -> categoryMatches(key, filter.mask()))
                .mapToLong(Long::longValue)
                .toArray();
    }

    @Override
    public List<ContactEvent> drainContactEvents() {
        List<ContactEvent> drained = List.copyOf(pendingContactEvents);
        pendingContactEvents.clear();
        return drained;
    }

    @Override
    public List<AreaEvent> drainAreaEvents() {
        List<AreaEvent> drained = List.copyOf(pendingAreaEvents);
        pendingAreaEvents.clear();
        return drained;
    }

    @Override
    public void setGravity(Vector3fc gravity) {
        world.setGravity(Box3dShapeAttacher.toVec(gravity));
    }

    @Override
    public void step(float stepSeconds) {
        world.step(stepSeconds, SUBSTEP_COUNT);
        collectContactEvents();
        collectSensorEvents();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bodies.clear();
        joints.clear();
        world.close();
        bakedShapeReleases.forEach(Runnable::run);
        bakedShapeReleases.clear();
    }

    private Box3dBodyState createState(B3BodyType type, RigidBodyPose pose, boolean area) {
        B3Body body = world.createBody(type, Box3dShapeAttacher.toVec(pose.position()));
        body.setTransform(Box3dShapeAttacher.toVec(pose.position()), toQuat(pose.rotation()));
        Box3dBodyState state = new Box3dBodyState(body, area);
        bodies.put(body.key(), state);
        return state;
    }

    private void attachShape(Box3dBodyState state, ShapeDescriptor shape, Vector3fc offset,
                             B3ShapeConfig config, boolean sensor) {
        B3ShapeConfig effective = sensor || state.area ? config.asSensor() : config;
        B3Shape attached = Box3dShapeAttacher.attach(state.body, shape, offset, effective, bakedShapeReleases);
        state.shapes.add(attached);
        state.categoryBits |= config.categoryBits();
        applyDesiredMass(state);
    }

    private void applyDesiredMass(Box3dBodyState state) {
        if (state.desiredMass <= 0.0f || state.body.type() != B3BodyType.DYNAMIC || state.shapes.isEmpty()) {
            return;
        }
        float currentMass = state.body.mass();
        if (currentMass <= 0.0f) {
            return;
        }
        float factor = state.desiredMass / currentMass;
        if (Math.abs(factor - 1.0f) < 1.0e-5f) {
            return;
        }
        state.shapeDensity *= factor;
        for (B3Shape shape : state.shapes) {
            shape.setDensity(state.shapeDensity);
        }
    }

    private static void applyDynamicProperties(B3Body body, DynamicProperties properties, boolean canSleep) {
        body.setGravityScale(properties.gravityScale());
        body.setLinearDamping(properties.linearDamping());
        body.setAngularDamping(properties.angularDamping());
        body.setBullet(properties.continuousCollisionDetection());
        if (!canSleep) {
            body.setSleepThreshold(0.0f);
        }
    }

    private void collectContactEvents() {
        B3Events.Contacts contacts = world.contactEvents();
        Map<BodyPair, B3Events.ContactHit> hitsByPair = new HashMap<>();
        for (B3Events.ContactHit hit : contacts.hits()) {
            if (hit.bodyA() != null && hit.bodyB() != null) {
                hitsByPair.put(BodyPair.of(hit.bodyA().key(), hit.bodyB().key()), hit);
            }
        }
        for (B3Events.ContactBegin begin : contacts.begins()) {
            addContactEvent(begin.bodyA(), begin.bodyB(), hitsByPair, true);
        }
        for (B3Events.ContactEnd end : contacts.ends()) {
            addContactEvent(end.bodyA(), end.bodyB(), hitsByPair, false);
        }
    }


    private static void fillFromManifold(B3Body first, B3Body second, Vector3f point, Vector3f normal) {
        List<B3Body.ContactPoint> touching = first.contactPoints();
        int matched = 0;
        for (B3Body.ContactPoint contact : touching) {
            if (contact.other() != second) {
                continue;
            }
            point.add((float) contact.point().x(), (float) contact.point().y(), (float) contact.point().z());
            normal.add((float) contact.normal().x(), (float) contact.normal().y(), (float) contact.normal().z());
            matched++;
        }
        if (matched == 0) {
            return;
        }
        point.div(matched);
        if (normal.lengthSquared() > 1.0e-8f) {
            normal.normalize();
        }
    }

    private void addContactEvent(B3Body first, B3Body second, Map<BodyPair, B3Events.ContactHit> hitsByPair,
                                 boolean started) {
        if (first == null || second == null) {
            return;
        }
        B3Events.ContactHit hit = hitsByPair.get(BodyPair.of(first.key(), second.key()));
        Vector3f point = hit != null ? toVector(hit.point()) : new Vector3f();
        Vector3f normal = hit != null ? toVector(hit.normal()) : new Vector3f();
        float approachSpeed = hit != null ? hit.approachSpeed() : 0.0f;
        if (hit == null && started) {
            fillFromManifold(first, second, point, normal);
        }
        pendingContactEvents.add(new ContactEvent(new BodyHandle(first.key()), new BodyHandle(second.key()),
                point, normal, approachSpeed, started));
    }

    private void collectSensorEvents() {
        List<B3World.SensorTouch>[] touches = world.sensorEvents();
        for (B3World.SensorTouch touch : touches[0]) {
            addAreaEvent(touch, true);
        }
        for (B3World.SensorTouch touch : touches[1]) {
            addAreaEvent(touch, false);
        }
    }

    private void addAreaEvent(B3World.SensorTouch touch, boolean entered) {
        if (touch.sensor() == null || touch.visitor() == null) {
            return;
        }
        pendingAreaEvents.add(new AreaEvent(new BodyHandle(touch.sensor().key()),
                new BodyHandle(touch.visitor().key()), entered));
    }

    private Box3dBodyState stateOf(BodyHandle handle) {
        Box3dBodyState state = bodies.get(handle.id());
        if (state == null) {
            throw new EpysiaException("Unknown physics body handle " + handle.id());
        }
        return state;
    }

    private boolean isAreaBody(long bodyKey) {
        Box3dBodyState state = bodies.get(bodyKey);
        return state != null && state.area;
    }

    private boolean categoryMatches(long bodyKey, int mask) {
        Box3dBodyState state = bodies.get(bodyKey);
        return state != null && (state.categoryBits & Integer.toUnsignedLong(mask)) != 0L;
    }

    private JointHandle registerJoint(B3Joint joint) {
        long id = nextJointId++;
        joints.put(id, joint);
        return new JointHandle(id);
    }

    private static BodyHandle handleOf(Box3dBodyState state) {
        return new BodyHandle(state.body.key());
    }

    private static B3BodyType bodyTypeOf(RigidBodyKind kind) {
        return switch (kind) {
            case DYNAMIC -> B3BodyType.DYNAMIC;
            case KINEMATIC -> B3BodyType.KINEMATIC;
            case STATIC, AREA -> B3BodyType.STATIC;
        };
    }

    private static B3ShapeConfig configFor(CollisionMask mask) {
        return B3ShapeConfig.defaults()
                .withFriction(DEFAULT_FRICTION)
                .withRestitution(0.0f)
                .withFilter(Integer.toUnsignedLong(mask.layer()), Integer.toUnsignedLong(mask.mask()));
    }

    private static B3Filter queryFilterOf(QueryFilter filter) {
        return new B3Filter(~0L, Integer.toUnsignedLong(filter.mask()));
    }

    private static Vec3 worldPointOf(B3Body body, Vector3fc localPoint) {
        Vec3 rotated = body.rotation().rotate(new Vec3(localPoint.x(), localPoint.y(), localPoint.z()));
        return rotated.add(body.position());
    }

    private static Vec3 scaled(Vector3f direction, float distance) {
        return new Vec3(direction.x * distance, direction.y * distance, direction.z * distance);
    }

    private static Vector3f toVector(Vec3 vector) {
        return new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
    }

    private static Quat toQuat(Quaternionfc rotation) {
        return new Quat(rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    private static Quaternionf toQuaternion(Quat rotation) {
        return new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.s());
    }

    private record BodyPair(long low, long high) {
        static BodyPair of(long first, long second) {
            return first <= second ? new BodyPair(first, second) : new BodyPair(second, first);
        }
    }

    @Override
    public void drawDebug(PhysicsDebugLines lines) {
        world.drawDebug(new B3DebugDraw() {
            @Override
            public void segment(float startX, float startY, float startZ,
                                float endX, float endY, float endZ, int color) {
                lines.segment(startX, startY, startZ, endX, endY, endZ, color);
            }

            @Override
            public com.meekdev.box3d.B3DebugFlags flags() {
                return DEBUG_FLAGS;
            }
        });
    }
}
