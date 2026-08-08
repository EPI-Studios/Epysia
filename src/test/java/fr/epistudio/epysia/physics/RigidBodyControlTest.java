package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.MotionLocks;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigidBodyControlTest {
    private static final float FRAME_SECONDS = 1.0f / 60.0f;
    private static final float START_HEIGHT = 10.0f;

    @Test
    void freezingVerticalPositionStopsTheBodyFalling() {
        Scene scene = new Scene("locks");
        RigidBodyComponent body = new RigidBodyComponent();
        body.setMotionLocks(new MotionLocks(false, true, false, false, false, false));
        scene.addGameObject(bodyObject(body));
        scene.advanceTick();
        PhysicsSystem physics = startedPhysics();

        for (int frame = 0; frame < 120; frame++) {
            physics.update(scene, null, FRAME_SECONDS);
        }

        assertEquals(START_HEIGHT, heightOf(scene), 1.0e-3f);
    }

    @Test
    void anImpulsePushedBeforeRegistrationIsReplayedNotLost() {
        Scene scene = new Scene("pending");
        RigidBodyComponent body = new RigidBodyComponent();
        body.addImpulse(new Vector3f(0.0f, 40.0f, 0.0f));
        scene.addGameObject(bodyObject(body));
        scene.advanceTick();
        PhysicsSystem physics = startedPhysics();

        physics.update(scene, null, FRAME_SECONDS);

        assertTrue(heightOf(scene) > START_HEIGHT,
                "the queued impulse never reached the body, height stayed at " + heightOf(scene));
    }

    private static PhysicsSystem startedPhysics() {
        PhysicsSystem physics = new PhysicsSystem();
        physics.initialize(null);
        physics.setFixedTimestepHertz(60);
        return physics;
    }

    private static GameObject bodyObject(RigidBodyComponent body) {
        GameObject object = new GameObject("body");
        object.addComponent(new Transform3D().setPosition(0.0f, START_HEIGHT, 0.0f));
        object.addComponent(body);
        object.addComponent(new BoxCollider());
        return object;
    }

    private static float heightOf(Scene scene) {
        return scene.gameObjects().getFirst().getComponent(Transform3D.class).orElseThrow().position().y();
    }
}
