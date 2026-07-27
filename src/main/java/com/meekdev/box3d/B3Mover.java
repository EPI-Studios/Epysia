package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3Capsule;
import com.meekdev.box3d.ffi.b3CollisionPlane;
import com.meekdev.box3d.ffi.b3MoverFilterFcn;
import com.meekdev.box3d.ffi.b3Plane;
import com.meekdev.box3d.ffi.b3PlaneResult;
import com.meekdev.box3d.ffi.b3PlaneResultFcn;
import com.meekdev.box3d.ffi.b3PlaneSolverResult;
import com.meekdev.box3d.ffi.b3Pos;
import com.meekdev.box3d.ffi.b3ShapeId;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

/** kinematic capsule character controller

    the collide, solve, cast loop from the box3d character sample with
    depenetration, step up, ground snapping and contact reporting, planes
    come back relative to the origin so everything internal is local floats */
public final class B3Mover implements AutoCloseable {

    private static final int PLANE_CAPACITY = 16;
    private static final int MAX_ITERATIONS = 5;
    private static final float TOLERANCE = 0.01f;
    private static final float MAX_DEPENETRATION = 0.25f;

    private final B3World world;
    private final Arena arena;
    private final MemorySegment capsule;
    private final MemorySegment planes;
    private final MemorySegment planeShapes;
    private final MemorySegment planePoints;
    private final MemorySegment filter;
    private final MemorySegment planeCallback;
    private final MemorySegment castFilterCallback;
    private final MemorySegment origin;
    private final MemorySegment scratchVec;
    private final float groundNormalY;
    private final float radius;
    private final float bottomCenterY;
    private int planeCount;
    private static final LongPredicate ACCEPT_EVERY_BODY = bodyKey -> true;

    private LongPredicate bodyFilter = ACCEPT_EVERY_BODY;
    private long excludedBody;

    public record Contact(B3Body body, Vec3 point, Vec3 normal) {}

    public record MoveResult(Vec3 position, Vec3 clippedDelta, boolean grounded, Vec3 groundNormal,
                             List<Contact> contacts) {}

    public B3Mover(B3World world, float radius, float halfHeight) {
        this(world, radius, halfHeight, 50f);
    }

    public B3Mover(B3World world, float radius, float halfHeight, float maxSlopeDegrees) {
        this.world = world;
        this.arena = Arena.ofConfined();
        this.groundNormalY = (float) Math.cos(Math.toRadians(maxSlopeDegrees));

        // capsule stands on the origin, lifted by a skin so the contact slop
        // never sinks the feet below the surface, vanilla anti clip hates that
        float skin = 0.01f;
        this.radius = radius;
        this.bottomCenterY = radius + skin;
        this.capsule = b3Capsule.allocate(arena);
        MemorySegment c1 = b3Capsule.center1(capsule);
        b3Vec3.x(c1, 0);
        b3Vec3.y(c1, radius + skin);
        b3Vec3.z(c1, 0);
        MemorySegment c2 = b3Capsule.center2(capsule);
        b3Vec3.x(c2, 0);
        b3Vec3.y(c2, 2 * halfHeight - radius);
        b3Vec3.z(c2, 0);
        b3Capsule.radius(capsule, radius);

        this.planes = b3CollisionPlane.allocateArray(PLANE_CAPACITY, arena);
        this.planeShapes = b3ShapeId.allocateArray(PLANE_CAPACITY, arena);
        this.planePoints = b3Vec3.allocateArray(PLANE_CAPACITY, arena);
        this.filter = box3d_h.b3DefaultQueryFilter(arena);
        this.origin = b3Pos.allocate(arena);
        this.scratchVec = b3Vec3.allocate(arena);

        // one upcall stub each for the mover's lifetime, never allocated per move
        this.planeCallback = b3PlaneResultFcn.allocate((shapeId, results, count, ctx) -> {
            if (!accepts(world.shapeBodyKey(shapeId))) {
                return true;
            }
            for (int i = 0; i < count && planeCount < PLANE_CAPACITY; i++) {
                MemorySegment result = b3PlaneResult.asSlice(results.reinterpret(
                        b3PlaneResult.sizeof() * count), i);
                MemorySegment dst = b3CollisionPlane.asSlice(planes, planeCount);
                MemorySegment.copy(b3PlaneResult.plane(result), 0,
                        b3CollisionPlane.plane(dst), 0, b3Plane.sizeof());
                b3CollisionPlane.pushLimit(dst, Float.MAX_VALUE);
                b3CollisionPlane.push(dst, 0f);
                b3CollisionPlane.clipVelocity(dst, true);
                MemorySegment.copy(shapeId, 0,
                        b3ShapeId.asSlice(planeShapes, planeCount), 0, b3ShapeId.sizeof());
                MemorySegment.copy(b3PlaneResult.point(result), 0,
                        b3Vec3.asSlice(planePoints, planeCount), 0, b3Vec3.sizeof());
                planeCount++;
            }
            return true;
        }, arena);

        this.castFilterCallback = b3MoverFilterFcn.allocate((shapeId, ctx) ->
                accepts(world.shapeBodyKey(shapeId)), arena);
    }

    private boolean accepts(long bodyKey) {
        return (excludedBody == 0 || bodyKey != excludedBody) && bodyFilter.test(bodyKey);
    }

    // reject bodies the caller wants the mover to pass through, one-way platforms for instance
    public void setBodyFilter(LongPredicate filter) {
        this.bodyFilter = filter == null ? ACCEPT_EVERY_BODY : filter;
    }

    // ignore this body in every query, use it for the mover's own mirrored capsule
    public void setExcludedBody(B3Body body) {
        this.excludedBody = body == null ? 0 : B3World.bodyKey(body.idSegment());
    }

    public MoveResult move(Vec3 position, Vec3 delta) {
        return move(position, delta, 0f, false);
    }

    public MoveResult move(Vec3 position, Vec3 delta, float stepHeight) {
        return move(position, delta, stepHeight, false);
    }

    public MoveResult move(Vec3 position, Vec3 delta, float stepHeight, boolean snapToGround) {
        double px = position.x();
        double py = position.y();
        double pz = position.z();
        float tx = (float) delta.x();
        float ty = (float) delta.y();
        float tz = (float) delta.z();

        try (Arena temp = Arena.ofConfined()) {
            // push out of any overlap first, otherwise the cast reports zero and jams
            collide(px, py, pz);
            if (planeCount > 0) {
                float[] push = solve(temp, 0, 0, 0);
                px += clampAbs(push[0], MAX_DEPENETRATION);
                py += clampAbs(push[1], MAX_DEPENETRATION);
                pz += clampAbs(push[2], MAX_DEPENETRATION);
            }

            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                collide(px, py, pz);
                float[] d = solve(temp, tx, ty, tz);
                float fraction = cast(px, py, pz, d[0], d[1], d[2]);
                float mx = fraction * d[0];
                float my = fraction * d[1];
                float mz = fraction * d[2];
                px += mx;
                py += my;
                pz += mz;
                tx -= mx;
                ty -= my;
                tz -= mz;
                if (mx * mx + my * my + mz * mz < TOLERANCE * TOLERANCE) {
                    break;
                }
            }

            // remaining horizontal motion against a wall, try climbing a step
            float remaining = (float) Math.sqrt(tx * tx + tz * tz);
            if (stepHeight > 0 && remaining > TOLERANCE) {
                float upFrac = cast(px, py, pz, 0, stepHeight, 0);
                float up = upFrac * stepHeight;
                if (up > TOLERANCE) {
                    float fwdFrac = cast(px, py + up, pz, tx, 0, tz);
                    float fx = fwdFrac * tx;
                    float fz = fwdFrac * tz;
                    if (fx * fx + fz * fz > TOLERANCE * TOLERANCE) {
                        double sx = px + fx;
                        double sz = pz + fz;
                        float downFrac = cast(sx, py + up, sz, 0, -up, 0);
                        px = sx;
                        py = py + up - downFrac * up;
                        pz = sz;
                    }
                }
            }

            // walking off a lip or down a slope, glue to the ground within step height
            if (snapToGround && stepHeight > 0) {
                collide(px, py, pz);
                if (!hasGroundPlane()) {
                    float downFrac = cast(px, py, pz, 0, -stepHeight, 0);
                    if (downFrac < 1f) {
                        py -= downFrac * stepHeight;
                    }
                }
            }

            // final plane set at the resting position for grounding and velocity clipping
            // the zero target solve fills in plane pushes, clip skips planes with none
            collide(px, py, pz);
            solve(temp, 0, 0, 0);

            b3Vec3.x(scratchVec, (float) delta.x());
            b3Vec3.y(scratchVec, (float) delta.y());
            b3Vec3.z(scratchVec, (float) delta.z());
            MemorySegment clipped = box3d_h.b3ClipVector(temp, scratchVec, planes, planeCount);

            boolean grounded = false;
            float gx = 0, gy = 0, gz = 0;
            float lift = 0;
            List<Contact> contacts = new ArrayList<>(planeCount);
            for (int i = 0; i < planeCount; i++) {
                MemorySegment plane = b3CollisionPlane.plane(b3CollisionPlane.asSlice(planes, i));
                MemorySegment normal = b3Plane.normal(plane);
                float nx = b3Vec3.x(normal);
                float ny = b3Vec3.y(normal);
                float nz = b3Vec3.z(normal);

                B3Body body = world.bodyByKey(world.shapeBodyKey(b3ShapeId.asSlice(planeShapes, i)));
                MemorySegment point = b3Vec3.asSlice(planePoints, i);
                contacts.add(new Contact(body,
                        new Vec3(px + b3Vec3.x(point), py + b3Vec3.y(point), pz + b3Vec3.z(point)),
                        new Vec3(nx, ny, nz)));

                if (ny > groundNormalY && ny > gy) {
                    grounded = true;
                    gx = nx;
                    gy = ny;
                    gz = nz;
                    // box3d leaves a slop of encroachment that vanilla's anti clip
                    // rejects, measure it against the ground plane and climb out
                    float d = ny * bottomCenterY - b3Plane.offset(plane);
                    lift = Math.max(0, Math.min(radius - d, 0.05f));
                }
            }
            if (lift > 0.002f) {
                py += lift;
            }

            return new MoveResult(new Vec3(px, py, pz),
                    new Vec3(b3Vec3.x(clipped), b3Vec3.y(clipped), b3Vec3.z(clipped)),
                    grounded, new Vec3(gx, gy, gz), contacts);
        }
    }

    // is there anything to stand on within depth below the given position
    public boolean groundBelow(Vec3 position, float depth) {
        return cast(position.x(), position.y(), position.z(), 0, -depth, 0) < 1f;
    }

    private boolean hasGroundPlane() {
        for (int i = 0; i < planeCount; i++) {
            MemorySegment normal = b3Plane.normal(b3CollisionPlane.plane(
                    b3CollisionPlane.asSlice(planes, i)));
            if (b3Vec3.y(normal) > groundNormalY) {
                return true;
            }
        }
        return false;
    }

    private void collide(double px, double py, double pz) {
        planeCount = 0;
        b3Pos.x(origin, px);
        b3Pos.y(origin, py);
        b3Pos.z(origin, pz);
        box3d_h.b3World_CollideMover(world.id(), origin, capsule, filter,
                planeCallback, MemorySegment.NULL);
    }

    private float[] solve(Arena temp, float tx, float ty, float tz) {
        b3Vec3.x(scratchVec, tx);
        b3Vec3.y(scratchVec, ty);
        b3Vec3.z(scratchVec, tz);
        MemorySegment solved = box3d_h.b3SolvePlanes(temp, scratchVec, planes, planeCount);
        MemorySegment d = b3PlaneSolverResult.delta(solved);
        return new float[] {b3Vec3.x(d), b3Vec3.y(d), b3Vec3.z(d)};
    }

    private float cast(double px, double py, double pz, float dx, float dy, float dz) {
        b3Pos.x(origin, px);
        b3Pos.y(origin, py);
        b3Pos.z(origin, pz);
        b3Vec3.x(scratchVec, dx);
        b3Vec3.y(scratchVec, dy);
        b3Vec3.z(scratchVec, dz);
        return box3d_h.b3World_CastMover(world.id(), origin, capsule, scratchVec,
                filter, castFilterCallback, MemorySegment.NULL);
    }

    private static float clampAbs(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    @Override
    public void close() {
        arena.close();
    }
}
