package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3AABB;
import com.meekdev.box3d.ffi.b3BodyDef;
import com.meekdev.box3d.ffi.b3BodyEvents;
import com.meekdev.box3d.ffi.b3BodyId;
import com.meekdev.box3d.ffi.b3BodyMoveEvent;
import com.meekdev.box3d.ffi.b3ContactBeginTouchEvent;
import com.meekdev.box3d.ffi.b3ContactEndTouchEvent;
import com.meekdev.box3d.ffi.b3ContactEvents;
import com.meekdev.box3d.ffi.b3ContactHitEvent;
import com.meekdev.box3d.ffi.b3DistanceJointDef;
import com.meekdev.box3d.ffi.b3JointDef;
import com.meekdev.box3d.ffi.b3OverlapResultFcn;
import com.meekdev.box3d.ffi.b3Quat;
import com.meekdev.box3d.ffi.b3RevoluteJointDef;
import com.meekdev.box3d.ffi.b3Transform;
import com.meekdev.box3d.ffi.b3WeldJointDef;
import com.meekdev.box3d.ffi.b3WorldTransform;
import com.meekdev.box3d.ffi.b3Pos;
import com.meekdev.box3d.ffi.b3RayResult;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.b3WorldDef;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class B3World implements AutoCloseable {

    private static final boolean DEBUG = Boolean.getBoolean("bkun.box3d.debug");
    private static final long SCRATCH_SIZE = 256;

    private final Arena arena;
    private final MemorySegment id;
    private final MemorySegment scratchBuffer;
    private final Thread owner;
    private final B3TaskSystem tasks;
    private final Map<Long, B3Body> bodies = new HashMap<>();
    private final List<B3Body> overlapHits = new ArrayList<>();
    private final MemorySegment overlapStub;
    private boolean closed;

    private B3World(Arena arena, MemorySegment id, B3TaskSystem tasks) {
        this.arena = arena;
        this.id = id;
        this.tasks = tasks;
        this.scratchBuffer = arena.allocate(SCRATCH_SIZE);
        this.owner = Thread.currentThread();
        this.overlapStub = b3OverlapResultFcn.allocate((shapeId, ctx) -> {
            B3Body body = bodyOfShape(shapeId);
            if (body != null && !overlapHits.contains(body)) {
                overlapHits.add(body);
            }
            return true;
        }, arena);
    }

    public static B3World create(Vec3 gravity) {
        return create(gravity, 1);
    }

    public static B3World create(Vec3 gravity, int workerCount) {
        NativeLoader.load();

        Arena arena = Arena.ofConfined();
        MemorySegment def = box3d_h.b3DefaultWorldDef(arena);
        MemorySegment gravitySeg = b3Vec3.allocate(arena);
        b3Vec3.x(gravitySeg, (float) gravity.x());
        b3Vec3.y(gravitySeg, (float) gravity.y());
        b3Vec3.z(gravitySeg, (float) gravity.z());
        b3WorldDef.gravity(def, gravitySeg);
        b3WorldDef.workerCount(def, workerCount);

        B3TaskSystem tasks = null;
        if (workerCount > 1) {
            tasks = new B3TaskSystem(arena, workerCount);
            b3WorldDef.enqueueTask(def, tasks.enqueueStub);
            b3WorldDef.finishTask(def, tasks.finishStub);
        }

        MemorySegment id = box3d_h.b3CreateWorldDoublePrecision(arena, def);
        return new B3World(arena, id, tasks);
    }

    private final List<java.util.function.Consumer<B3Events.ContactBegin>> beginListeners = new ArrayList<>();
    private final List<java.util.function.Consumer<B3Events.ContactHit>> hitListeners = new ArrayList<>();

    public void onContactBegin(java.util.function.Consumer<B3Events.ContactBegin> listener) {
        beginListeners.add(listener);
    }

    public void onContactHit(java.util.function.Consumer<B3Events.ContactHit> listener) {
        hitListeners.add(listener);
    }

    // pairs the callback rejects never collide, evaluated when contacts are created
    public void setContactFilter(java.util.function.BiPredicate<B3Body, B3Body> filter) {
        checkThread();
        MemorySegment stub = com.meekdev.box3d.ffi.b3CustomFilterFcn.allocate((shapeA, shapeB, ctx) -> {
            B3Body a = bodyByKey(shapeBodyKey(shapeA));
            B3Body b = bodyByKey(shapeBodyKey(shapeB));
            return a == null || b == null || filter.test(a, b);
        }, arena);
        box3d_h.b3World_SetCustomFilterCallback(id, stub, MemorySegment.NULL);
    }

    /** mixes friction between two touching materials, runs on worker threads so keep it pure */
    public interface MaterialMix {
        float mix(float a, long userMaterialIdA, float b, long userMaterialIdB);
    }

    public void setFrictionMixer(MaterialMix mixer) {
        checkThread();
        MemorySegment stub = com.meekdev.box3d.ffi.b3FrictionCallback.allocate(
                (fa, ida, fb, idb) -> mixer.mix(fa, ida, fb, idb), arena);
        box3d_h.b3World_SetFrictionCallback(id, stub);
    }

    public void setRestitutionMixer(MaterialMix mixer) {
        checkThread();
        MemorySegment stub = com.meekdev.box3d.ffi.b3RestitutionCallback.allocate(
                (ra, ida, rb, idb) -> mixer.mix(ra, ida, rb, idb), arena);
        box3d_h.b3World_SetRestitutionCallback(id, stub);
    }

    public interface PreSolve {
        boolean keep(B3Body a, B3Body b, Vec3 point, Vec3 normal);
    }

    // runs during the step, return false to void the contact, keep it cheap
    public void setPreSolve(PreSolve callback) {
        checkThread();
        MemorySegment stub = com.meekdev.box3d.ffi.b3PreSolveFcn.allocate((shapeA, shapeB, point, normal, ctx) -> {
            B3Body a = bodyByKey(shapeBodyKey(shapeA));
            B3Body b = bodyByKey(shapeBodyKey(shapeB));
            return a == null || b == null || callback.keep(a, b,
                    new Vec3(b3Pos.x(point), b3Pos.y(point), b3Pos.z(point)),
                    new Vec3(b3Vec3.x(normal), b3Vec3.y(normal), b3Vec3.z(normal)));
        }, arena);
        box3d_h.b3World_SetPreSolveCallback(id, stub, MemorySegment.NULL);
    }

    public void step(float dt, int substeps) {
        checkThread();
        box3d_h.b3World_Step(id, dt, substeps);
        if (!beginListeners.isEmpty() || !hitListeners.isEmpty()) {
            B3Events.Contacts contacts = contactEvents();
            for (var listener : beginListeners) {
                contacts.begins().forEach(listener);
            }
            for (var listener : hitListeners) {
                contacts.hits().forEach(listener);
            }
        }
    }

    public Vec3 gravity() {
        checkThread();
        MemorySegment g = box3d_h.b3World_GetGravity(scratch(), id);
        return new Vec3(b3Vec3.x(g), b3Vec3.y(g), b3Vec3.z(g));
    }

    public void setGravity(Vec3 g) {
        checkThread();
        MemorySegment seg = b3Vec3.allocate(scratch());
        b3Vec3.x(seg, (float) g.x());
        b3Vec3.y(seg, (float) g.y());
        b3Vec3.z(seg, (float) g.z());
        box3d_h.b3World_SetGravity(id, seg);
    }

    public B3Body createBody(B3BodyType type, Vec3 pos) {
        checkThread();
        MemorySegment def = box3d_h.b3DefaultBodyDef(scratch());
        b3BodyDef.type(def, type.ordinal());
        MemorySegment posSeg = b3BodyDef.position(def);
        b3Pos.x(posSeg, pos.x());
        b3Pos.y(posSeg, pos.y());
        b3Pos.z(posSeg, pos.z());

        MemorySegment bodyId = box3d_h.b3CreateBody(arena, id, def);
        B3Body body = new B3Body(this, bodyId);
        bodies.put(bodyKey(bodyId), body);
        return body;
    }

    static long bodyKey(MemorySegment bodyId) {
        return ((long) b3BodyId.index1(bodyId) << 16) | (b3BodyId.generation(bodyId) & 0xFFFFL);
    }

    void forgetBody(MemorySegment bodyId) {
        bodies.remove(bodyKey(bodyId));
    }

    long shapeBodyKey(MemorySegment shapeId) {
        try (Arena temp = Arena.ofConfined()) {
            return bodyKey(box3d_h.b3Shape_GetBody(temp, shapeId));
        }
    }

    B3Body bodyByKey(long key) {
        return bodies.get(key);
    }

    private B3Body bodyOfShape(MemorySegment shapeId) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment bodyId = box3d_h.b3Shape_GetBody(temp, shapeId);
            return bodies.get(bodyKey(bodyId));
        }
    }

    // transient, read right after step and before the next one
    public B3Events.Contacts contactEvents() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment events = box3d_h.b3World_GetContactEvents(temp, id);
            int beginCount = b3ContactEvents.beginCount(events);
            int endCount = b3ContactEvents.endCount(events);
            int hitCount = b3ContactEvents.hitCount(events);

            List<B3Events.ContactBegin> begins = new ArrayList<>(beginCount);
            MemorySegment beginArray = b3ContactEvents.beginEvents(events)
                    .reinterpret(b3ContactBeginTouchEvent.sizeof() * Math.max(1, beginCount));
            for (int i = 0; i < beginCount; i++) {
                MemorySegment e = b3ContactBeginTouchEvent.asSlice(beginArray, i);
                begins.add(new B3Events.ContactBegin(
                        bodyOfShape(b3ContactBeginTouchEvent.shapeIdA(e)),
                        bodyOfShape(b3ContactBeginTouchEvent.shapeIdB(e))));
            }

            List<B3Events.ContactEnd> ends = new ArrayList<>(endCount);
            MemorySegment endArray = b3ContactEvents.endEvents(events)
                    .reinterpret(b3ContactEndTouchEvent.sizeof() * Math.max(1, endCount));
            for (int i = 0; i < endCount; i++) {
                MemorySegment e = b3ContactEndTouchEvent.asSlice(endArray, i);
                ends.add(new B3Events.ContactEnd(
                        bodyOfShape(b3ContactEndTouchEvent.shapeIdA(e)),
                        bodyOfShape(b3ContactEndTouchEvent.shapeIdB(e))));
            }

            List<B3Events.ContactHit> hits = new ArrayList<>(hitCount);
            MemorySegment hitArray = b3ContactEvents.hitEvents(events)
                    .reinterpret(b3ContactHitEvent.sizeof() * Math.max(1, hitCount));
            for (int i = 0; i < hitCount; i++) {
                MemorySegment e = b3ContactHitEvent.asSlice(hitArray, i);
                MemorySegment point = b3ContactHitEvent.point(e);
                MemorySegment normal = b3ContactHitEvent.normal(e);
                hits.add(new B3Events.ContactHit(
                        bodyOfShape(b3ContactHitEvent.shapeIdA(e)),
                        bodyOfShape(b3ContactHitEvent.shapeIdB(e)),
                        new Vec3(b3Pos.x(point), b3Pos.y(point), b3Pos.z(point)),
                        new Vec3(b3Vec3.x(normal), b3Vec3.y(normal), b3Vec3.z(normal)),
                        b3ContactHitEvent.approachSpeed(e)));
            }
            return new B3Events.Contacts(begins, ends, hits);
        }
    }

    // one entry per body that moved or changed sleep state this step
    public List<B3Events.BodyMove> bodyEvents() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment events = box3d_h.b3World_GetBodyEvents(temp, id);
            int count = b3BodyEvents.moveCount(events);
            List<B3Events.BodyMove> moves = new ArrayList<>(count);
            MemorySegment array = b3BodyEvents.moveEvents(events)
                    .reinterpret(b3BodyMoveEvent.sizeof() * Math.max(1, count));
            for (int i = 0; i < count; i++) {
                MemorySegment e = b3BodyMoveEvent.asSlice(array, i);
                B3Body body = bodies.get(bodyKey(b3BodyMoveEvent.bodyId(e)));
                if (body == null) {
                    continue;
                }
                MemorySegment transform = b3BodyMoveEvent.transform(e);
                MemorySegment p = b3WorldTransform.p(transform);
                MemorySegment q = b3WorldTransform.q(transform);
                MemorySegment qv = b3Quat.v(q);
                moves.add(new B3Events.BodyMove(body,
                        new Vec3(b3Pos.x(p), b3Pos.y(p), b3Pos.z(p)),
                        new Quat(b3Vec3.x(qv), b3Vec3.y(qv), b3Vec3.z(qv), b3Quat.s(q)),
                        b3BodyMoveEvent.fellAsleep(e)));
            }
            return moves;
        }
    }

    // bodies whose shapes may touch the box, broad phase only
    public List<B3Body> overlapAABB(Vec3 min, Vec3 max) {
        checkThread();
        overlapHits.clear();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment aabb = temp.allocate(b3AABB.layout());
            MemorySegment lo = b3AABB.lowerBound(aabb);
            b3Vec3.x(lo, (float) min.x());
            b3Vec3.y(lo, (float) min.y());
            b3Vec3.z(lo, (float) min.z());
            MemorySegment hi = b3AABB.upperBound(aabb);
            b3Vec3.x(hi, (float) max.x());
            b3Vec3.y(hi, (float) max.y());
            b3Vec3.z(hi, (float) max.z());
            MemorySegment filter = box3d_h.b3DefaultQueryFilter(temp);
            box3d_h.b3World_OverlapAABB(temp, id, aabb, filter, overlapStub, MemorySegment.NULL);
            return new ArrayList<>(overlapHits);
        }
    }

    public B3DistanceJoint createDistanceJoint(B3Body a, B3Body b, Vec3 anchorA, Vec3 anchorB, float length) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultDistanceJointDef(temp);
            MemorySegment base = b3DistanceJointDef.base(def);
            Quat axis = Quat.zTo(anchorB.sub(anchorA));
            fillBase(base, a, b, a.localPoint(anchorA), b.localPoint(anchorB), axis);
            b3DistanceJointDef.length(def, length);
            return new B3DistanceJoint(box3d_h.b3CreateDistanceJoint(arena, id, def));
        }
    }

    // hinge about the given world axis through the world pivot
    public B3RevoluteJoint createRevoluteJoint(B3Body a, B3Body b, Vec3 worldPivot, Vec3 worldAxis) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultRevoluteJointDef(temp);
            MemorySegment base = b3RevoluteJointDef.base(def);
            fillBaseAtPivot(base, a, b, worldPivot, Quat.zTo(worldAxis));
            return new B3RevoluteJoint(box3d_h.b3CreateRevoluteJoint(arena, id, def));
        }
    }

    public B3WeldJoint createWeldJoint(B3Body a, B3Body b, Vec3 worldPivot) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultWeldJointDef(temp);
            MemorySegment base = b3WeldJointDef.base(def);
            fillBaseAtPivot(base, a, b, worldPivot, Quat.identity);
            return new B3WeldJoint(box3d_h.b3CreateWeldJoint(arena, id, def));
        }
    }

    private void fillBaseAtPivot(MemorySegment base, B3Body a, B3Body b, Vec3 worldPivot, Quat worldFrame) {
        Vec3 localA = a.localPoint(worldPivot);
        Vec3 localB = b.localPoint(worldPivot);
        fillBase(base, a, b, localA, localB, worldFrame);
    }

    private void fillBase(MemorySegment base, B3Body a, B3Body b, Vec3 localA, Vec3 localB, Quat worldFrame) {
        b3JointDef.bodyIdA(base, a.idSegment());
        b3JointDef.bodyIdB(base, b.idSegment());
        writeFrame(b3JointDef.localFrameA(base), localA, a.rotation().conjugate().mul(worldFrame));
        writeFrame(b3JointDef.localFrameB(base), localB, b.rotation().conjugate().mul(worldFrame));
    }

    private static void writeFrame(MemorySegment frame, Vec3 p, Quat q) {
        MemorySegment pos = b3Transform.p(frame);
        b3Vec3.x(pos, (float) p.x());
        b3Vec3.y(pos, (float) p.y());
        b3Vec3.z(pos, (float) p.z());
        MemorySegment rot = b3Transform.q(frame);
        MemorySegment v = b3Quat.v(rot);
        b3Vec3.x(v, q.x());
        b3Vec3.y(v, q.y());
        b3Vec3.z(v, q.z());
        b3Quat.s(rot, q.s());
    }

    public B3PrismaticJoint createPrismaticJoint(B3Body a, B3Body b, Vec3 worldPivot, Vec3 worldAxis) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultPrismaticJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3PrismaticJointDef.base(def);
            fillBaseAtPivot(base, a, b, worldPivot, Quat.zTo(worldAxis));
            return new B3PrismaticJoint(box3d_h.b3CreatePrismaticJoint(arena, id, def));
        }
    }

    public B3SphericalJoint createSphericalJoint(B3Body a, B3Body b, Vec3 worldPivot) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultSphericalJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3SphericalJointDef.base(def);
            fillBaseAtPivot(base, a, b, worldPivot, Quat.identity);
            return new B3SphericalJoint(box3d_h.b3CreateSphericalJoint(arena, id, def));
        }
    }

    public B3MotorJoint createMotorJoint(B3Body a, B3Body b) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultMotorJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3MotorJointDef.base(def);
            fillBaseAtPivot(base, a, b, b.position(), Quat.identity);
            return new B3MotorJoint(box3d_h.b3CreateMotorJoint(arena, id, def));
        }
    }

    // a is the chassis, b the wheel, suspension along worldUp, wheel spins about worldAxle
    public B3WheelJoint createWheelJoint(B3Body a, B3Body b, Vec3 worldPivot, Vec3 worldUp, Vec3 worldAxle) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultWheelJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3WheelJointDef.base(def);
            Vec3 localA = a.localPoint(worldPivot);
            Vec3 localB = b.localPoint(worldPivot);
            b3JointDef.bodyIdA(base, a.idSegment());
            b3JointDef.bodyIdB(base, b.idSegment());
            // frame a: x axis is the suspension direction, frame b: z axis is the axle
            writeFrame(b3JointDef.localFrameA(base), localA, a.rotation().conjugate().mul(Quat.xTo(worldUp)));
            writeFrame(b3JointDef.localFrameB(base), localB, b.rotation().conjugate().mul(Quat.zTo(worldAxle)));
            return new B3WheelJoint(box3d_h.b3CreateWheelJoint(arena, id, def));
        }
    }

    // radial impulse on everything in range, falls off to zero past radius plus falloff
    public void explode(Vec3 center, float radius, float falloff, float impulsePerArea) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultExplosionDef(temp);
            MemorySegment pos = com.meekdev.box3d.ffi.b3ExplosionDef.position(def);
            b3Pos.x(pos, center.x());
            b3Pos.y(pos, center.y());
            b3Pos.z(pos, center.z());
            com.meekdev.box3d.ffi.b3ExplosionDef.radius(def, radius);
            com.meekdev.box3d.ffi.b3ExplosionDef.falloff(def, falloff);
            com.meekdev.box3d.ffi.b3ExplosionDef.impulsePerArea(def, impulsePerArea);
            box3d_h.b3World_Explode(id, def);
        }
    }

    public record StepProfile(float step, float collide, float solve, float transforms) {}

    public StepProfile profile() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment p = box3d_h.b3World_GetProfile(temp, id);
            return new StepProfile(
                    com.meekdev.box3d.ffi.b3Profile.step(p),
                    com.meekdev.box3d.ffi.b3Profile.collide(p),
                    com.meekdev.box3d.ffi.b3Profile.solve(p),
                    com.meekdev.box3d.ffi.b3Profile.transforms(p));
        }
    }

    public record Counters(int bodies, int shapes, int contacts, int joints, int islands) {}

    public Counters counters() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment c = box3d_h.b3World_GetCounters(temp, id);
            return new Counters(
                    com.meekdev.box3d.ffi.b3Counters.bodyCount(c),
                    com.meekdev.box3d.ffi.b3Counters.shapeCount(c),
                    com.meekdev.box3d.ffi.b3Counters.contactCount(c),
                    com.meekdev.box3d.ffi.b3Counters.jointCount(c),
                    com.meekdev.box3d.ffi.b3Counters.islandCount(c));
        }
    }

    public B3FilterJoint createFilterJoint(B3Body a, B3Body b) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultFilterJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3FilterJointDef.base(def);
            b3JointDef.bodyIdA(base, a.idSegment());
            b3JointDef.bodyIdB(base, b.idSegment());
            return new B3FilterJoint(box3d_h.b3CreateFilterJoint(arena, id, def));
        }
    }

    public B3ParallelJoint createParallelJoint(B3Body a, B3Body b) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = box3d_h.b3DefaultParallelJointDef(temp);
            MemorySegment base = com.meekdev.box3d.ffi.b3ParallelJointDef.base(def);
            fillBaseAtPivot(base, a, b, b.position(), Quat.identity);
            return new B3ParallelJoint(box3d_h.b3CreateParallelJoint(arena, id, def));
        }
    }

    private MemorySegment queryFilter(Arena temp, B3Filter filter) {
        MemorySegment seg = box3d_h.b3DefaultQueryFilter(temp);
        com.meekdev.box3d.ffi.b3QueryFilter.categoryBits(seg, filter.categoryBits());
        com.meekdev.box3d.ffi.b3QueryFilter.maskBits(seg, filter.maskBits());
        return seg;
    }

    public record ShapeHit(boolean hit, B3Body body, Vec3 point, Vec3 normal, double fraction) {}

    // sweep a sphere through the world and keep the first thing it touches
    public ShapeHit castSphereClosest(Vec3 origin, float radius, Vec3 translation, B3Filter filter) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment center = b3Vec3.allocate(temp);
            b3Vec3.x(center, 0);
            b3Vec3.y(center, 0);
            b3Vec3.z(center, 0);
            MemorySegment proxy = temp.allocate(com.meekdev.box3d.ffi.b3ShapeProxy.layout());
            com.meekdev.box3d.ffi.b3ShapeProxy.points(proxy, center);
            com.meekdev.box3d.ffi.b3ShapeProxy.count(proxy, 1);
            com.meekdev.box3d.ffi.b3ShapeProxy.radius(proxy, radius);

            MemorySegment originSeg = b3Pos.allocate(temp);
            b3Pos.x(originSeg, origin.x());
            b3Pos.y(originSeg, origin.y());
            b3Pos.z(originSeg, origin.z());
            MemorySegment trans = b3Vec3.allocate(temp);
            b3Vec3.x(trans, (float) translation.x());
            b3Vec3.y(trans, (float) translation.y());
            b3Vec3.z(trans, (float) translation.z());

            castHit = null;
            castFraction = 1f;
            MemorySegment fcn = com.meekdev.box3d.ffi.b3CastResultFcn.allocate(
                    (shapeId, point, normal, fraction, material, triangle, child, ctx) -> {
                        castHit = new ShapeHit(true, bodyOfShape(shapeId),
                                new Vec3(b3Pos.x(point), b3Pos.y(point), b3Pos.z(point)),
                                new Vec3(b3Vec3.x(normal), b3Vec3.y(normal), b3Vec3.z(normal)),
                                fraction);
                        castFraction = fraction;
                        return fraction;
                    }, temp);
            box3d_h.b3World_CastShape(temp, id, originSeg, proxy, trans, queryFilter(temp, filter),
                    fcn, MemorySegment.NULL);
            return castHit != null ? castHit : new ShapeHit(false, null, origin, Vec3.zero, 1.0);
        }
    }

    private ShapeHit castHit;
    private float castFraction;

    public record SensorTouch(B3Body sensor, B3Body visitor) {}

    // transient sensor begin and end touches from the last step
    public List<SensorTouch>[] sensorEvents() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment events = box3d_h.b3World_GetSensorEvents(temp, id);
            int beginCount = com.meekdev.box3d.ffi.b3SensorEvents.beginCount(events);
            int endCount = com.meekdev.box3d.ffi.b3SensorEvents.endCount(events);

            List<SensorTouch> begins = new ArrayList<>(beginCount);
            MemorySegment beginArray = com.meekdev.box3d.ffi.b3SensorEvents.beginEvents(events)
                    .reinterpret(com.meekdev.box3d.ffi.b3SensorBeginTouchEvent.sizeof() * Math.max(1, beginCount));
            for (int i = 0; i < beginCount; i++) {
                MemorySegment e = com.meekdev.box3d.ffi.b3SensorBeginTouchEvent.asSlice(beginArray, i);
                begins.add(new SensorTouch(
                        bodyOfShape(com.meekdev.box3d.ffi.b3SensorBeginTouchEvent.sensorShapeId(e)),
                        bodyOfShape(com.meekdev.box3d.ffi.b3SensorBeginTouchEvent.visitorShapeId(e))));
            }
            List<SensorTouch> ends = new ArrayList<>(endCount);
            MemorySegment endArray = com.meekdev.box3d.ffi.b3SensorEvents.endEvents(events)
                    .reinterpret(com.meekdev.box3d.ffi.b3SensorEndTouchEvent.sizeof() * Math.max(1, endCount));
            for (int i = 0; i < endCount; i++) {
                MemorySegment e = com.meekdev.box3d.ffi.b3SensorEndTouchEvent.asSlice(endArray, i);
                ends.add(new SensorTouch(
                        bodyOfShape(com.meekdev.box3d.ffi.b3SensorEndTouchEvent.sensorShapeId(e)),
                        bodyOfShape(com.meekdev.box3d.ffi.b3SensorEndTouchEvent.visitorShapeId(e))));
            }
            @SuppressWarnings("unchecked")
            List<SensorTouch>[] result = new List[] {begins, ends};
            return result;
        }
    }

    // joints whose force or torque threshold tripped during the last step
    public List<B3Joint> jointEvents() {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment events = box3d_h.b3World_GetJointEvents(temp, id);
            int count = com.meekdev.box3d.ffi.b3JointEvents.count(events);
            List<B3Joint> tripped = new ArrayList<>(count);
            MemorySegment array = com.meekdev.box3d.ffi.b3JointEvents.jointEvents(events)
                    .reinterpret(com.meekdev.box3d.ffi.b3JointEvent.sizeof() * Math.max(1, count));
            for (int i = 0; i < count; i++) {
                MemorySegment e = com.meekdev.box3d.ffi.b3JointEvent.asSlice(array, i);
                MemorySegment jointId = arena.allocate(com.meekdev.box3d.ffi.b3JointId.layout());
                MemorySegment.copy(com.meekdev.box3d.ffi.b3JointEvent.jointId(e), 0,
                        jointId, 0, com.meekdev.box3d.ffi.b3JointId.sizeof());
                tripped.add(new B3Joint(jointId));
            }
            return tripped;
        }
    }

    public void rebuildStaticTree() {
        checkThread();
        box3d_h.b3World_RebuildStaticTree(id);
    }

    public void startRecording(B3Recording recording) {
        checkThread();
        box3d_h.b3World_StartRecording(id, recording.handle());
    }

    public void stopRecording() {
        checkThread();
        box3d_h.b3World_StopRecording(id);
    }

    public record RayHit(boolean hit, Vec3 point, Vec3 normal, double fraction, B3Body body) {}

    public RayHit castRayClosest(Vec3 origin, Vec3 translation) {
        return castRayClosest(origin, translation, B3Filter.everything);
    }

    public RayHit castRayClosest(Vec3 origin, Vec3 translation, B3Filter queryFilter) {
        checkThread();
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment originSeg = b3Pos.allocate(temp);
            b3Pos.x(originSeg, origin.x());
            b3Pos.y(originSeg, origin.y());
            b3Pos.z(originSeg, origin.z());

            MemorySegment transSeg = b3Vec3.allocate(temp);
            b3Vec3.x(transSeg, (float) translation.x());
            b3Vec3.y(transSeg, (float) translation.y());
            b3Vec3.z(transSeg, (float) translation.z());

            MemorySegment filter = queryFilter(temp, queryFilter);
            MemorySegment result = box3d_h.b3World_CastRayClosest(temp, id, originSeg, transSeg, filter);

            boolean hit = b3RayResult.hit(result);
            MemorySegment point = b3RayResult.point(result);
            MemorySegment normal = b3RayResult.normal(result);
            return new RayHit(hit,
                    new Vec3(b3Pos.x(point), b3Pos.y(point), b3Pos.z(point)),
                    new Vec3(b3Vec3.x(normal), b3Vec3.y(normal), b3Vec3.z(normal)),
                    b3RayResult.fraction(result),
                    hit ? bodyOfShape(b3RayResult.shapeId(result)) : null);
        }
    }

    public boolean isValid() {
        return !closed && box3d_h.b3World_IsValid(id);
    }

    @Override
    public void close() {
        checkThread();
        if (closed) {
            return;
        }
        box3d_h.b3DestroyWorld(id);
        if (tasks != null) {
            tasks.close();
        }
        arena.close();
        closed = true;
    }

    MemorySegment id() {
        return id;
    }

    Arena arena() {
        return arena;
    }

    // fresh slicing view over the same buffer each call, so callers get a clean
    // allocator without the arena growing unbounded across steps
    SegmentAllocator scratch() {
        return SegmentAllocator.slicingAllocator(scratchBuffer);
    }

    private void checkThread() {
        if (DEBUG && Thread.currentThread() != owner) {
            throw new AssertionError("box3d world accessed from non-owner thread");
        }
    }
}
