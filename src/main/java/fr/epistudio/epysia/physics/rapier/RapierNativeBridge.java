package fr.epistudio.epysia.physics.rapier;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class RapierNativeBridge {

    static {
        RapierNativeLibrary.load();
    }

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();
    private static final Linker.Option CRITICAL = Linker.Option.critical(true);

    private RapierNativeBridge() {
    }

    private static MethodHandle bind(String symbolName, FunctionDescriptor descriptor, Linker.Option... options) {
        return LOOKUP.find(symbolName)
                .map(segment -> LINKER.downcallHandle(segment, descriptor, options))
                .orElseThrow(() -> new EpysiaException("Missing native symbol: " + symbolName));
    }

    private static final MethodHandle MH_VERSION = bind("rapier_version", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle MH_WORLD_NEW = bind("rapier_world_new", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle MH_WORLD_DROP = bind("rapier_world_drop", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_WORLD_STEP = bind("rapier_world_step", FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));
    private static final MethodHandle MH_WORLD_SET_GRAVITY = bind("rapier_world_set_gravity",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final FunctionDescriptor BODY_ADD_BASIC = FunctionDescriptor.of(JAVA_LONG,
            ADDRESS, JAVA_LONG,
            JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
            JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
            JAVA_INT, JAVA_INT);

    private static final MethodHandle MH_BODY_ADD_STATIC = bind("rapier_body_add_static", BODY_ADD_BASIC);
    private static final MethodHandle MH_BODY_ADD_KINEMATIC = bind("rapier_body_add_kinematic", BODY_ADD_BASIC);
    private static final MethodHandle MH_BODY_ADD_AREA = bind("rapier_body_add_area", BODY_ADD_BASIC);

    private static final MethodHandle MH_BODY_ADD_DYNAMIC = bind("rapier_body_add_dynamic",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, JAVA_INT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT));

    private static final MethodHandle MH_BODY_REMOVE = bind("rapier_body_remove",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_BODY_SET_XFORM = bind("rapier_body_set_xform",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));

    private static final MethodHandle MH_BODY_GET_XFORM = bind("rapier_body_get_xform",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS), CRITICAL);

    private static final FunctionDescriptor APPLY_VEC = FunctionDescriptor.ofVoid(
            ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);

    private static final MethodHandle MH_BODY_APPLY_FORCE = bind("rapier_body_apply_force", APPLY_VEC);
    private static final MethodHandle MH_BODY_APPLY_IMPULSE = bind("rapier_body_apply_impulse", APPLY_VEC);
    private static final MethodHandle MH_BODY_SET_LINVEL = bind("rapier_body_set_linvel", APPLY_VEC);

    private static final MethodHandle MH_BODY_GET_LINVEL = bind("rapier_body_get_linvel",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS), CRITICAL);

    private static final MethodHandle MH_BODY_SLEEP = bind("rapier_body_sleep",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_BODY_WAKE = bind("rapier_body_wake",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_JOINT_ADD_FIXED = bind("rapier_joint_add_fixed",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT));

    private static final MethodHandle MH_JOINT_ADD_SPHERICAL = bind("rapier_joint_add_spherical",
            FunctionDescriptor.of(JAVA_LONG,
                    ADDRESS, JAVA_LONG, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT));

    private static final MethodHandle MH_JOINT_REMOVE = bind("rapier_joint_remove",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_QUERY_RAYCAST = bind("rapier_query_raycast",
            FunctionDescriptor.of(JAVA_BOOLEAN,
                    ADDRESS,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_INT, ADDRESS), CRITICAL);

    private static final MethodHandle MH_QUERY_SHAPE_CAST = bind("rapier_query_shape_cast",
            FunctionDescriptor.of(JAVA_BOOLEAN,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_INT, ADDRESS));

    private static final MethodHandle MH_QUERY_OVERLAP = bind("rapier_query_overlap",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_SHAPE_BOX = bind("rapier_shape_box",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
    private static final MethodHandle MH_SHAPE_SPHERE = bind("rapier_shape_sphere",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT));
    private static final MethodHandle MH_SHAPE_CAPSULE = bind("rapier_shape_capsule",
            FunctionDescriptor.of(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT));
    private static final MethodHandle MH_SHAPE_TRIMESH = bind("rapier_shape_trimesh",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle MH_SHAPE_CONVEX_HULL = bind("rapier_shape_convex_hull",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle MH_SHAPE_DROP = bind("rapier_shape_drop",
            FunctionDescriptor.ofVoid(JAVA_LONG));

    private static final MethodHandle MH_CHARCTL_NEW = bind("rapier_charctl_new",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle MH_CHARCTL_DROP = bind("rapier_charctl_drop",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MH_CHARCTL_MOVE = bind("rapier_charctl_move",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, ADDRESS));
    private static final MethodHandle MH_CHARCTL_MOVE_SHAPE = bind("rapier_charctl_move_shape",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT,
                    JAVA_FLOAT, ADDRESS));

    private static final MethodHandle MH_EVENTS_DRAIN_CONTACTS = bind("rapier_events_drain_contacts",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle MH_EVENTS_DRAIN_AREAS = bind("rapier_events_drain_areas",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    public static int version() {
        return invokeInt(MH_VERSION, "rapier_version");
    }

    public static MemorySegment worldNew() {
        try {
            return (MemorySegment) MH_WORLD_NEW.invokeExact();
        } catch (Throwable cause) {
            throw failure("rapier_world_new", cause);
        }
    }

    public static void worldDrop(MemorySegment world) {
        try {
            MH_WORLD_DROP.invokeExact(world);
        } catch (Throwable cause) {
            throw failure("rapier_world_drop", cause);
        }
    }

    public static void worldStep(MemorySegment world, float stepSeconds) {
        try {
            MH_WORLD_STEP.invokeExact(world, stepSeconds);
        } catch (Throwable cause) {
            throw failure("rapier_world_step", cause);
        }
    }

    public static void worldSetGravity(MemorySegment world, float gravityX, float gravityY, float gravityZ) {
        try {
            MH_WORLD_SET_GRAVITY.invokeExact(world, gravityX, gravityY, gravityZ);
        } catch (Throwable cause) {
            throw failure("rapier_world_set_gravity", cause);
        }
    }

    public static long bodyAddStatic(MemorySegment world, long shape,
                                     float positionX, float positionY, float positionZ,
                                     float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                     int layer, int mask) {
        return invokeBodyAdd(MH_BODY_ADD_STATIC, "rapier_body_add_static",
                world, shape, positionX, positionY, positionZ,
                quaternionX, quaternionY, quaternionZ, quaternionW, layer, mask);
    }

    public static long bodyAddKinematic(MemorySegment world, long shape,
                                        float positionX, float positionY, float positionZ,
                                        float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                        int layer, int mask) {
        return invokeBodyAdd(MH_BODY_ADD_KINEMATIC, "rapier_body_add_kinematic",
                world, shape, positionX, positionY, positionZ,
                quaternionX, quaternionY, quaternionZ, quaternionW, layer, mask);
    }

    public static long bodyAddArea(MemorySegment world, long shape,
                                   float positionX, float positionY, float positionZ,
                                   float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                   int layer, int mask) {
        return invokeBodyAdd(MH_BODY_ADD_AREA, "rapier_body_add_area",
                world, shape, positionX, positionY, positionZ,
                quaternionX, quaternionY, quaternionZ, quaternionW, layer, mask);
    }

    private static long invokeBodyAdd(MethodHandle handle, String symbolName,
                                      MemorySegment world, long shape,
                                      float positionX, float positionY, float positionZ,
                                      float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                      int layer, int mask) {
        try {
            return (long) handle.invokeExact(world, shape,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    layer, mask);
        } catch (Throwable cause) {
            throw failure(symbolName, cause);
        }
    }

    public static long bodyAddDynamic(MemorySegment world, long shape,
                                      float positionX, float positionY, float positionZ,
                                      float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                      int layer, int mask,
                                      float mass, float gravityScale,
                                      float linearDamping, float angularDamping,
                                      int continuousCollisionDetection) {
        try {
            return (long) MH_BODY_ADD_DYNAMIC.invokeExact(world, shape,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    layer, mask, mass, gravityScale, linearDamping, angularDamping,
                    continuousCollisionDetection);
        } catch (Throwable cause) {
            throw failure("rapier_body_add_dynamic", cause);
        }
    }

    public static void bodyRemove(MemorySegment world, long body) {
        try {
            MH_BODY_REMOVE.invokeExact(world, body);
        } catch (Throwable cause) {
            throw failure("rapier_body_remove", cause);
        }
    }

    public static void bodySetTransform(MemorySegment world, long body,
                                        float positionX, float positionY, float positionZ,
                                        float quaternionX, float quaternionY, float quaternionZ, float quaternionW) {
        try {
            MH_BODY_SET_XFORM.invokeExact(world, body,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW);
        } catch (Throwable cause) {
            throw failure("rapier_body_set_xform", cause);
        }
    }

    public static void bodyGetTransform(MemorySegment world, long body, MemorySegment outputBuffer) {
        try {
            MH_BODY_GET_XFORM.invokeExact(world, body, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_body_get_xform", cause);
        }
    }

    public static void bodyApplyForce(MemorySegment world, long body, float forceX, float forceY, float forceZ) {
        invokeApplyVector(MH_BODY_APPLY_FORCE, "rapier_body_apply_force", world, body, forceX, forceY, forceZ);
    }

    public static void bodyApplyImpulse(MemorySegment world, long body, float impulseX, float impulseY, float impulseZ) {
        invokeApplyVector(MH_BODY_APPLY_IMPULSE, "rapier_body_apply_impulse", world, body, impulseX, impulseY, impulseZ);
    }

    public static void bodySetLinearVelocity(MemorySegment world, long body, float velocityX, float velocityY, float velocityZ) {
        invokeApplyVector(MH_BODY_SET_LINVEL, "rapier_body_set_linvel", world, body, velocityX, velocityY, velocityZ);
    }

    private static void invokeApplyVector(MethodHandle handle, String symbolName,
                                          MemorySegment world, long body, float x, float y, float z) {
        try {
            handle.invokeExact(world, body, x, y, z);
        } catch (Throwable cause) {
            throw failure(symbolName, cause);
        }
    }

    public static void bodyGetLinearVelocity(MemorySegment world, long body, MemorySegment outputBuffer) {
        try {
            MH_BODY_GET_LINVEL.invokeExact(world, body, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_body_get_linvel", cause);
        }
    }

    public static void bodySleep(MemorySegment world, long body) {
        try {
            MH_BODY_SLEEP.invokeExact(world, body);
        } catch (Throwable cause) {
            throw failure("rapier_body_sleep", cause);
        }
    }

    public static void bodyWake(MemorySegment world, long body) {
        try {
            MH_BODY_WAKE.invokeExact(world, body);
        } catch (Throwable cause) {
            throw failure("rapier_body_wake", cause);
        }
    }

    public static long jointAddFixed(MemorySegment world, long first, long second,
                                     float anchorAx, float anchorAy, float anchorAz,
                                     float anchorAqx, float anchorAqy, float anchorAqz, float anchorAqw,
                                     float anchorBx, float anchorBy, float anchorBz,
                                     float anchorBqx, float anchorBqy, float anchorBqz, float anchorBqw,
                                     int contactsEnabled) {
        try {
            return (long) MH_JOINT_ADD_FIXED.invokeExact(world, first, second,
                    anchorAx, anchorAy, anchorAz,
                    anchorAqx, anchorAqy, anchorAqz, anchorAqw,
                    anchorBx, anchorBy, anchorBz,
                    anchorBqx, anchorBqy, anchorBqz, anchorBqw,
                    contactsEnabled);
        } catch (Throwable cause) {
            throw failure("rapier_joint_add_fixed", cause);
        }
    }

    public static long jointAddSpherical(MemorySegment world, long first, long second,
                                         float anchorAx, float anchorAy, float anchorAz,
                                         float anchorBx, float anchorBy, float anchorBz,
                                         int contactsEnabled) {
        try {
            return (long) MH_JOINT_ADD_SPHERICAL.invokeExact(world, first, second,
                    anchorAx, anchorAy, anchorAz,
                    anchorBx, anchorBy, anchorBz,
                    contactsEnabled);
        } catch (Throwable cause) {
            throw failure("rapier_joint_add_spherical", cause);
        }
    }

    public static void jointRemove(MemorySegment world, long joint) {
        try {
            MH_JOINT_REMOVE.invokeExact(world, joint);
        } catch (Throwable cause) {
            throw failure("rapier_joint_remove", cause);
        }
    }

    public static boolean queryRaycast(MemorySegment world,
                                       float originX, float originY, float originZ,
                                       float directionX, float directionY, float directionZ,
                                       float maxDistance, int mask, MemorySegment outputBuffer) {
        try {
            return (boolean) MH_QUERY_RAYCAST.invokeExact(world,
                    originX, originY, originZ,
                    directionX, directionY, directionZ,
                    maxDistance, mask, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_query_raycast", cause);
        }
    }

    public static boolean queryShapeCast(MemorySegment world, long shape,
                                         float positionX, float positionY, float positionZ,
                                         float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                         float directionX, float directionY, float directionZ,
                                         float maxDistance, int mask, MemorySegment outputBuffer) {
        try {
            return (boolean) MH_QUERY_SHAPE_CAST.invokeExact(world, shape,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    directionX, directionY, directionZ,
                    maxDistance, mask, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_query_shape_cast", cause);
        }
    }

    public static int queryOverlap(MemorySegment world, long shape,
                                   float positionX, float positionY, float positionZ,
                                   float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                   int mask, MemorySegment outputBuffer, int maxResults) {
        try {
            return (int) MH_QUERY_OVERLAP.invokeExact(world, shape,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    mask, outputBuffer, maxResults);
        } catch (Throwable cause) {
            throw failure("rapier_query_overlap", cause);
        }
    }

    public static long shapeBox(float halfExtentX, float halfExtentY, float halfExtentZ) {
        try {
            return (long) MH_SHAPE_BOX.invokeExact(halfExtentX, halfExtentY, halfExtentZ);
        } catch (Throwable cause) {
            throw failure("rapier_shape_box", cause);
        }
    }

    public static long shapeSphere(float radius) {
        try {
            return (long) MH_SHAPE_SPHERE.invokeExact(radius);
        } catch (Throwable cause) {
            throw failure("rapier_shape_sphere", cause);
        }
    }

    public static long shapeCapsule(float radius, float halfHeight) {
        try {
            return (long) MH_SHAPE_CAPSULE.invokeExact(radius, halfHeight);
        } catch (Throwable cause) {
            throw failure("rapier_shape_capsule", cause);
        }
    }

    public static long shapeTriangleMesh(MemorySegment vertices, int vertexFloatCount,
                                         MemorySegment indices, int indexCount) {
        try {
            return (long) MH_SHAPE_TRIMESH.invokeExact(vertices, vertexFloatCount, indices, indexCount);
        } catch (Throwable cause) {
            throw failure("rapier_shape_trimesh", cause);
        }
    }

    public static long shapeConvexHull(MemorySegment vertices, int vertexFloatCount) {
        try {
            return (long) MH_SHAPE_CONVEX_HULL.invokeExact(vertices, vertexFloatCount);
        } catch (Throwable cause) {
            throw failure("rapier_shape_convex_hull", cause);
        }
    }

    public static void shapeDrop(long shape) {
        try {
            MH_SHAPE_DROP.invokeExact(shape);
        } catch (Throwable cause) {
            throw failure("rapier_shape_drop", cause);
        }
    }

    public static MemorySegment characterControllerNew(MemorySegment world) {
        try {
            return (MemorySegment) MH_CHARCTL_NEW.invokeExact(world);
        } catch (Throwable cause) {
            throw failure("rapier_charctl_new", cause);
        }
    }

    public static void characterControllerDrop(MemorySegment controller) {
        try {
            MH_CHARCTL_DROP.invokeExact(controller);
        } catch (Throwable cause) {
            throw failure("rapier_charctl_drop", cause);
        }
    }

    public static void characterControllerMove(MemorySegment controller, MemorySegment world, long body,
                                               float desiredX, float desiredY, float desiredZ,
                                               float stepSeconds, MemorySegment outputBuffer) {
        try {
            MH_CHARCTL_MOVE.invokeExact(controller, world, body,
                    desiredX, desiredY, desiredZ, stepSeconds, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_charctl_move", cause);
        }
    }

    public static void characterControllerMoveShape(MemorySegment controller, MemorySegment world, long shape,
                                                    float positionX, float positionY, float positionZ,
                                                    float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                                                    float desiredX, float desiredY, float desiredZ,
                                                    float stepSeconds, MemorySegment outputBuffer) {
        try {
            MH_CHARCTL_MOVE_SHAPE.invokeExact(controller, world, shape,
                    positionX, positionY, positionZ,
                    quaternionX, quaternionY, quaternionZ, quaternionW,
                    desiredX, desiredY, desiredZ,
                    stepSeconds, outputBuffer);
        } catch (Throwable cause) {
            throw failure("rapier_charctl_move_shape", cause);
        }
    }

    public static int eventsDrainContacts(MemorySegment world, MemorySegment outputBuffer, int maxEvents) {
        try {
            return (int) MH_EVENTS_DRAIN_CONTACTS.invokeExact(world, outputBuffer, maxEvents);
        } catch (Throwable cause) {
            throw failure("rapier_events_drain_contacts", cause);
        }
    }

    public static int eventsDrainAreas(MemorySegment world, MemorySegment outputBuffer, int maxEvents) {
        try {
            return (int) MH_EVENTS_DRAIN_AREAS.invokeExact(world, outputBuffer, maxEvents);
        } catch (Throwable cause) {
            throw failure("rapier_events_drain_areas", cause);
        }
    }

    private static int invokeInt(MethodHandle handle, String symbolName) {
        try {
            return (int) handle.invokeExact();
        } catch (Throwable cause) {
            throw failure(symbolName, cause);
        }
    }

    private static EpysiaException failure(String symbolName, Throwable cause) {
        return new EpysiaException("Native call failed: " + symbolName, cause);
    }
}
