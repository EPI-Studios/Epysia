package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CollisionMask;
import fr.epistudio.epysia.physics.api.ContactImpulseSnapshot;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactImpulseSnapshotTest {
    private static final float STEP_SECONDS = 1.0f / 60.0f;
    private static final int SETTLE_STEPS = 180;
    private static final float BOX_HALF_HEIGHT = 0.5f;

    @Test
    void aRestingBodyHasContactsWorthSaving() {
        try (PhysicsWorld world = settledWorld()) {
            BodyHandle resting = world.addDynamicBody(new ShapeDescriptor.Box(halfExtents()),
                    poseAt(0.0f, 1.0f + BOX_HALF_HEIGHT, 0.0f), DynamicProperties.defaults(),
                    CollisionMask.DEFAULT);
            settle(world);
            try (ContactImpulseSnapshot snapshot = world.saveContactImpulses(resting)) {
                assertTrue(snapshot.contactCount() > 0,
                        "a box resting on the floor should have at least one contact");
            }
        }
    }

    @Test
    void savedImpulsesWriteBackThroughTheNativeBoundary() {
        try (PhysicsWorld world = settledWorld()) {
            BodyHandle resting = world.addDynamicBody(new ShapeDescriptor.Box(halfExtents()),
                    poseAt(0.0f, 1.0f + BOX_HALF_HEIGHT, 0.0f), DynamicProperties.defaults(),
                    CollisionMask.DEFAULT);
            settle(world);
            try (ContactImpulseSnapshot snapshot = world.saveContactImpulses(resting)) {
                world.step(STEP_SECONDS);
                assertTrue(snapshot.restore() > 0,
                        "the native call should have written impulses onto surviving manifold points");
            }
        }
    }

    @Test
    void aBodyTouchingNothingSavesNothingAndRestoresNothing() {
        try (PhysicsWorld world = settledWorld()) {
            BodyHandle floating = world.addDynamicBody(new ShapeDescriptor.Box(halfExtents()),
                    poseAt(0.0f, 40.0f, 0.0f), DynamicProperties.defaults(), CollisionMask.DEFAULT);
            world.step(STEP_SECONDS);
            try (ContactImpulseSnapshot snapshot = world.saveContactImpulses(floating)) {
                assertEquals(0, snapshot.contactCount());
                assertEquals(0, snapshot.restore());
            }
        }
    }

    private static PhysicsWorld settledWorld() {
        PhysicsWorld world = new Box3dPhysicsWorld();
        world.setGravity(new Vector3f(0.0f, -9.81f, 0.0f));
        world.addStaticBody(new ShapeDescriptor.Box(new Vector3f(20.0f, 0.5f, 20.0f)),
                poseAt(0.0f, 0.5f, 0.0f), CollisionMask.DEFAULT);
        return world;
    }

    private static void settle(PhysicsWorld world) {
        for (int step = 0; step < SETTLE_STEPS; step++) {
            world.step(STEP_SECONDS);
        }
    }

    private static Vector3f halfExtents() {
        return new Vector3f(0.5f, BOX_HALF_HEIGHT, 0.5f);
    }

    private static RigidBodyPose poseAt(float x, float y, float z) {
        return new RigidBodyPose(new Vector3f(x, y, z), new Quaternionf());
    }
}
