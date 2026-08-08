package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.physics.api.BodyHandle;
import fr.epistudio.epysia.physics.api.CollisionMask;
import fr.epistudio.epysia.physics.api.DynamicProperties;
import fr.epistudio.epysia.physics.api.PhysicsWorld;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.box3d.Box3dPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Box3dBindingsSmokeTest {
    private static final float STEP_SECONDS = 1.0f / 60.0f;
    private static final int STEP_COUNT = 120;
    private static final float FLOOR_TOP = 1.0f;
    private static final float DROP_HEIGHT = 6.0f;

    @Test
    void aDroppedBodyFallsAndRestsOnTheFloor() {
        try (PhysicsWorld world = new Box3dPhysicsWorld()) {
            world.setGravity(new Vector3f(0.0f, -9.81f, 0.0f));
            world.addStaticBody(new ShapeDescriptor.Box(new Vector3f(20.0f, 0.5f, 20.0f)),
                    poseAt(0.0f, 0.5f, 0.0f), CollisionMask.DEFAULT);
            BodyHandle falling = world.addDynamicBody(new ShapeDescriptor.Sphere(0.5f),
                    poseAt(0.0f, DROP_HEIGHT, 0.0f), DynamicProperties.defaults(), CollisionMask.DEFAULT);

            for (int step = 0; step < STEP_COUNT; step++) {
                world.step(STEP_SECONDS);
            }

            float restingHeight = world.getBodyPose(falling).position().y();
            assertTrue(restingHeight < DROP_HEIGHT - 1.0f,
                    "body never fell, it stayed at " + restingHeight);
            assertTrue(restingHeight > FLOOR_TOP - 0.5f,
                    "body fell through the floor, it ended at " + restingHeight);
        }
    }

    private static RigidBodyPose poseAt(float x, float y, float z) {
        return new RigidBodyPose(new Vector3f(x, y, z), new Quaternionf());
    }
}
