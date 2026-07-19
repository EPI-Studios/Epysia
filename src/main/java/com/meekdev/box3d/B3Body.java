package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3BoxHull;
import com.meekdev.box3d.ffi.b3Capsule;
import com.meekdev.box3d.ffi.b3Pos;
import com.meekdev.box3d.ffi.b3Quat;
import com.meekdev.box3d.ffi.b3ShapeDef;
import com.meekdev.box3d.ffi.b3Sphere;
import com.meekdev.box3d.ffi.b3SurfaceMaterial;
import com.meekdev.box3d.ffi.b3Vec3;
import com.meekdev.box3d.ffi.b3WorldTransform;
import com.meekdev.box3d.ffi.box3d_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/** a rigid body handle, shapes are added after creation like the native api expects */
public final class B3Body {

    private final B3World world;
    private final MemorySegment id;

    B3Body(B3World world, MemorySegment id) {
        this.world = world;
        this.id = id;
    }

    public B3Shape addBox(Vec3 halfExtents) {
        return addBox(halfExtents, B3ShapeConfig.defaults());
    }

    // boxes are hulls under the hood, b3MakeBoxHull builds the 8-vertex hull for us
    public B3Shape addBox(Vec3 halfExtents, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment boxHull = box3d_h.b3MakeBoxHull(temp,
                    (float) halfExtents.x(), (float) halfExtents.y(), (float) halfExtents.z());
            MemorySegment hullData = b3BoxHull.base(boxHull);
            MemorySegment shapeId = box3d_h.b3CreateHullShape(world.arena(), id, def, hullData);
            return new B3Shape(shapeId);
        }
    }

    public B3Shape addBoxAt(Vec3 localCenter, Vec3 halfExtents) {
        return addBoxAt(localCenter, halfExtents, B3ShapeConfig.defaults());
    }

    public B3Shape addBoxAt(Vec3 localCenter, Vec3 halfExtents, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment boxHull = box3d_h.b3MakeOffsetBoxHull(temp,
                    (float) halfExtents.x(), (float) halfExtents.y(), (float) halfExtents.z(),
                    vec3(temp, localCenter));
            MemorySegment hullData = b3BoxHull.base(boxHull);
            MemorySegment shapeId = box3d_h.b3CreateHullShape(world.arena(), id, def, hullData);
            return new B3Shape(shapeId);
        }
    }

    public B3Shape addCapsule(Vec3 c1, Vec3 c2, float radius) {
        return addCapsule(c1, c2, radius, B3ShapeConfig.defaults());
    }

    public B3Shape addCapsule(Vec3 c1, Vec3 c2, float radius, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment capsule = b3Capsule.allocate(temp);
            b3Capsule.center1(capsule, vec3(temp, c1));
            b3Capsule.center2(capsule, vec3(temp, c2));
            b3Capsule.radius(capsule, radius);
            MemorySegment shapeId = box3d_h.b3CreateCapsuleShape(world.arena(), id, def, capsule);
            return new B3Shape(shapeId);
        }
    }

    public B3Shape addSphere(float radius) {
        return addSphere(radius, B3ShapeConfig.defaults());
    }

    public B3Shape addSphereAt(Vec3 center, float radius) {
        return addSphereAt(center, radius, B3ShapeConfig.defaults());
    }

    public B3Shape addSphereAt(Vec3 center, float radius, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment sphere = b3Sphere.allocate(temp);
            b3Sphere.center(sphere, vec3(temp, center));
            b3Sphere.radius(sphere, radius);
            return new B3Shape(box3d_h.b3CreateSphereShape(world.arena(), id, def, sphere));
        }
    }

    public B3Shape addSphere(float radius, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment sphere = b3Sphere.allocate(temp);
            b3Sphere.center(sphere, vec3(temp, new Vec3(0, 0, 0)));
            b3Sphere.radius(sphere, radius);
            MemorySegment shapeId = box3d_h.b3CreateSphereShape(world.arena(), id, def, sphere);
            return new B3Shape(shapeId);
        }
    }

    public B3Shape addHull(B3Hull hull) {
        return addHull(hull, B3ShapeConfig.defaults());
    }

    public B3Shape addHull(B3Hull hull, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            return new B3Shape(box3d_h.b3CreateHullShape(world.arena(), id, def, hull.data()));
        }
    }

    public B3Shape addMesh(B3Mesh mesh) {
        return addMesh(mesh, B3ShapeConfig.defaults());
    }

    public B3Shape addMesh(B3Mesh mesh, B3ShapeConfig config) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment scale = vec3(temp, new Vec3(1, 1, 1));
            MemorySegment shapeId = box3d_h.b3CreateMeshShape(world.arena(), id, def, mesh.data(), scale);
            return new B3Shape(shapeId);
        }
    }

    public Vec3 position() {
        MemorySegment pos = box3d_h.b3Body_GetPosition(world.scratch(), id);
        return new Vec3(b3Pos.x(pos), b3Pos.y(pos), b3Pos.z(pos));
    }

    public Quat rotation() {
        MemorySegment rot = box3d_h.b3Body_GetRotation(world.scratch(), id);
        MemorySegment v = b3Quat.v(rot);
        return new Quat(b3Vec3.x(v), b3Vec3.y(v), b3Vec3.z(v), b3Quat.s(rot));
    }

    public Vec3 linearVelocity() {
        MemorySegment vel = box3d_h.b3Body_GetLinearVelocity(world.scratch(), id);
        return new Vec3(b3Vec3.x(vel), b3Vec3.y(vel), b3Vec3.z(vel));
    }

    public void setLinearVelocity(Vec3 v) {
        MemorySegment seg = b3Vec3.allocate(world.scratch());
        b3Vec3.x(seg, (float) v.x());
        b3Vec3.y(seg, (float) v.y());
        b3Vec3.z(seg, (float) v.z());
        box3d_h.b3Body_SetLinearVelocity(id, seg);
    }

    public void setTransform(Vec3 pos, Quat rot) {
        SegmentAllocator alloc = world.scratch();
        MemorySegment posSeg = b3Pos.allocate(alloc);
        b3Pos.x(posSeg, pos.x());
        b3Pos.y(posSeg, pos.y());
        b3Pos.z(posSeg, pos.z());

        MemorySegment rotSeg = b3Quat.allocate(alloc);
        MemorySegment v = b3Quat.v(rotSeg);
        b3Vec3.x(v, rot.x());
        b3Vec3.y(v, rot.y());
        b3Vec3.z(v, rot.z());
        b3Quat.s(rotSeg, rot.s());

        box3d_h.b3Body_SetTransform(id, posSeg, rotSeg);
    }

    public float mass() {
        return box3d_h.b3Body_GetMass(id);
    }

    public Vec3 angularVelocity() {
        MemorySegment w = box3d_h.b3Body_GetAngularVelocity(world.scratch(), id);
        return new Vec3(b3Vec3.x(w), b3Vec3.y(w), b3Vec3.z(w));
    }

    public void setAngularVelocity(Vec3 w) {
        box3d_h.b3Body_SetAngularVelocity(id, vec3(world.scratch(), w));
    }

    public Vec3 velocityAtPoint(Vec3 worldPoint) {
        SegmentAllocator alloc = world.scratch();
        MemorySegment v = box3d_h.b3Body_GetWorldPointVelocity(alloc, id, pos(alloc, worldPoint));
        return new Vec3(b3Vec3.x(v), b3Vec3.y(v), b3Vec3.z(v));
    }

    public void applyForceToCenter(Vec3 force) {
        box3d_h.b3Body_ApplyForceToCenter(id, vec3(world.scratch(), force), true);
    }

    // impulse at an off center point spins the body naturally
    public void applyImpulseAt(Vec3 impulse, Vec3 worldPoint) {
        SegmentAllocator alloc = world.scratch();
        box3d_h.b3Body_ApplyLinearImpulse(id, vec3(alloc, impulse), pos(alloc, worldPoint), true);
    }

    public B3Shape addHeightField(B3Heightfield field) {
        return addHeightField(field, B3ShapeConfig.defaults());
    }

    public B3Shape addHeightField(B3Heightfield field, B3ShapeConfig config) {
        if (type() != B3BodyType.STATIC) {
            throw new IllegalStateException("height fields only go on static bodies");
        }
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment shapeId = box3d_h.b3CreateHeightFieldShape(world.arena(), id, def, field.data());
            return new B3Shape(shapeId);
        }
    }

    public Vec3 localPoint(Vec3 worldPoint) {
        SegmentAllocator alloc = world.scratch();
        MemorySegment local = box3d_h.b3Body_GetLocalPoint(alloc, id, pos(alloc, worldPoint));
        return new Vec3(b3Vec3.x(local), b3Vec3.y(local), b3Vec3.z(local));
    }

    // kinematic bodies chase this transform with real velocity so contacts push properly
    public void setTargetTransform(Vec3 pos, Quat rot, float dt) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment target = temp.allocate(b3WorldTransform.layout());
            MemorySegment p = b3WorldTransform.p(target);
            b3Pos.x(p, pos.x());
            b3Pos.y(p, pos.y());
            b3Pos.z(p, pos.z());
            MemorySegment q = b3WorldTransform.q(target);
            MemorySegment v = b3Quat.v(q);
            b3Vec3.x(v, rot.x());
            b3Vec3.y(v, rot.y());
            b3Vec3.z(v, rot.z());
            b3Quat.s(q, rot.s());
            box3d_h.b3Body_SetTargetTransform(id, target, dt, true);
        }
    }

    public B3Shape addCompound(B3Compound compound) {
        return addCompound(compound, B3ShapeConfig.defaults());
    }

    // box3d restricts compounds to static bodies, add several shapes for a dynamic assembly
    public B3Shape addCompound(B3Compound compound, B3ShapeConfig config) {
        if (type() != B3BodyType.STATIC) {
            throw new IllegalStateException("compound shapes only go on static bodies");
        }
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            MemorySegment shapeId = box3d_h.b3CreateCompoundShape(world.arena(), id, def, compound.data());
            return new B3Shape(shapeId);
        }
    }

    public B3Shape addMesh(B3Mesh mesh, Vec3 scale, B3ShapeConfig config) {
        return addMesh(mesh, scale, config, (B3SurfaceMat[]) null);
    }

    // frictions line up with the mesh's material indices, entry zero is the default
    public B3Shape addMesh(B3Mesh mesh, Vec3 scale, B3ShapeConfig config, float[] frictions) {
        B3SurfaceMat[] mats = null;
        if (frictions != null) {
            mats = new B3SurfaceMat[frictions.length];
            for (int i = 0; i < frictions.length; i++) {
                mats[i] = B3SurfaceMat.of(frictions[i]);
            }
        }
        return addMesh(mesh, scale, config, mats);
    }

    /** materials line up with the mesh's per triangle material indices */
    public B3Shape addMesh(B3Mesh mesh, Vec3 scale, B3ShapeConfig config, B3SurfaceMat[] materials) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment def = shapeDef(temp, config);
            if (materials != null && materials.length > 0) {
                MemorySegment array = b3SurfaceMaterial.allocateArray(materials.length, temp);
                for (int i = 0; i < materials.length; i++) {
                    MemorySegment mat = b3SurfaceMaterial.asSlice(array, i);
                    MemorySegment.copy(box3d_h.b3DefaultSurfaceMaterial(temp), 0, mat, 0,
                            b3SurfaceMaterial.sizeof());
                    b3SurfaceMaterial.friction(mat, materials[i].friction());
                    b3SurfaceMaterial.restitution(mat, materials[i].restitution());
                    b3SurfaceMaterial.rollingResistance(mat, materials[i].rollingResistance());
                }
                b3ShapeDef.materials(def, array);
                b3ShapeDef.materialCount(def, materials.length);
            }
            MemorySegment shapeId = box3d_h.b3CreateMeshShape(world.arena(), id, def, mesh.data(),
                    vec3(temp, scale));
            return new B3Shape(shapeId);
        }
    }

    /** overrides the mass derived from shapes, center is body local */
    public void setMass(float mass, Vec3 localCenter) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment data = box3d_h.b3Body_GetMassData(temp, id);
            com.meekdev.box3d.ffi.b3MassData.mass(data, mass);
            MemorySegment center = com.meekdev.box3d.ffi.b3MassData.center(data);
            b3Vec3.x(center, (float) localCenter.x());
            b3Vec3.y(center, (float) localCenter.y());
            b3Vec3.z(center, (float) localCenter.z());
            box3d_h.b3Body_SetMassData(id, data);
        }
    }

    /** recompute mass and inertia from the attached shapes */
    public void recomputeMass() {
        box3d_h.b3Body_ApplyMassFromShapes(id);
    }

    public void setLinearDamping(float damping) {
        box3d_h.b3Body_SetLinearDamping(id, damping);
    }

    public void setAngularDamping(float damping) {
        box3d_h.b3Body_SetAngularDamping(id, damping);
    }

    public void setGravityScale(float scale) {
        box3d_h.b3Body_SetGravityScale(id, scale);
    }

    // continuous collision against other dynamics, for small fast things
    public void setBullet(boolean bullet) {
        box3d_h.b3Body_SetBullet(id, bullet);
    }

    public void setSleepThreshold(float speed) {
        box3d_h.b3Body_SetSleepThreshold(id, speed);
    }

    // freeze chosen degrees of freedom, handy for 2d style constraints
    public void setMotionLocks(boolean linearX, boolean linearY, boolean linearZ,
                               boolean angularX, boolean angularY, boolean angularZ) {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment locks = temp.allocate(com.meekdev.box3d.ffi.b3MotionLocks.layout());
            com.meekdev.box3d.ffi.b3MotionLocks.linearX(locks, linearX);
            com.meekdev.box3d.ffi.b3MotionLocks.linearY(locks, linearY);
            com.meekdev.box3d.ffi.b3MotionLocks.linearZ(locks, linearZ);
            com.meekdev.box3d.ffi.b3MotionLocks.angularX(locks, angularX);
            com.meekdev.box3d.ffi.b3MotionLocks.angularY(locks, angularY);
            com.meekdev.box3d.ffi.b3MotionLocks.angularZ(locks, angularZ);
            box3d_h.b3Body_SetMotionLocks(id, locks);
        }
    }

    public void setName(String name) {
        try (Arena temp = Arena.ofConfined()) {
            box3d_h.b3Body_SetName(id, temp.allocateFrom(name));
        }
    }

    public boolean isValid() {
        return box3d_h.b3Body_IsValid(id);
    }

    // world space bounds over every shape on the body
    public Vec3[] aabb() {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment box = box3d_h.b3Body_ComputeAABB(temp, id);
            MemorySegment lo = com.meekdev.box3d.ffi.b3AABB.lowerBound(box);
            MemorySegment hi = com.meekdev.box3d.ffi.b3AABB.upperBound(box);
            return new Vec3[] {
                    new Vec3(b3Vec3.x(lo), b3Vec3.y(lo), b3Vec3.z(lo)),
                    new Vec3(b3Vec3.x(hi), b3Vec3.y(hi), b3Vec3.z(hi))};
        }
    }

    public void setType(B3BodyType type) {
        box3d_h.b3Body_SetType(id, type.ordinal());
    }

    public B3BodyType type() {
        return B3BodyType.values()[box3d_h.b3Body_GetType(id)];
    }

    public boolean isAwake() {
        return box3d_h.b3Body_IsAwake(id);
    }

    public void setAwake(boolean awake) {
        box3d_h.b3Body_SetAwake(id, awake);
    }

    MemorySegment idSegment() {
        return id;
    }

    // stable identity for maps, unique within a world
    public long key() {
        return B3World.bodyKey(id);
    }

    public void destroy() {
        world.forgetBody(id);
        box3d_h.b3DestroyBody(id);
    }

    private static MemorySegment pos(SegmentAllocator allocator, Vec3 v) {
        MemorySegment seg = b3Pos.allocate(allocator);
        b3Pos.x(seg, v.x());
        b3Pos.y(seg, v.y());
        b3Pos.z(seg, v.z());
        return seg;
    }

    private static MemorySegment vec3(SegmentAllocator allocator, Vec3 v) {
        MemorySegment seg = b3Vec3.allocate(allocator);
        b3Vec3.x(seg, (float) v.x());
        b3Vec3.y(seg, (float) v.y());
        b3Vec3.z(seg, (float) v.z());
        return seg;
    }

    // updateBodyMass is on by default in b3DefaultShapeDef so mass updates automatically per shape add
    private static MemorySegment shapeDef(SegmentAllocator allocator, B3ShapeConfig config) {
        MemorySegment def = box3d_h.b3DefaultShapeDef(allocator);
        b3ShapeDef.density(def, config.density());
        // off by default in box3d, we want the java side to see contacts
        b3ShapeDef.enableContactEvents(def, true);
        b3ShapeDef.enableHitEvents(def, true);
        b3ShapeDef.enableSensorEvents(def, true);
        b3ShapeDef.enableCustomFiltering(def, true);
        MemorySegment filter = b3ShapeDef.filter(def);
        com.meekdev.box3d.ffi.b3Filter.categoryBits(filter, config.categoryBits());
        com.meekdev.box3d.ffi.b3Filter.maskBits(filter, config.maskBits());
        if (config.sensor()) {
            b3ShapeDef.isSensor(def, true);
        }
        MemorySegment mat = b3ShapeDef.baseMaterial(def);
        b3SurfaceMaterial.friction(mat, config.friction());
        b3SurfaceMaterial.restitution(mat, config.restitution());
        return def;
    }
}
