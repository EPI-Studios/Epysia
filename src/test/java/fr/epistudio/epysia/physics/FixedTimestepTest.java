package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedTimestepTest {
    private static final float SIMULATED_SECONDS = 6.0f;
    private static final float DROP_HEIGHT = 12.0f;
    private static final float TOLERANCE = 0.05f;

    @Test
    void theSameFallSettlesAtTheSameHeightAtAnyFrameRate() {
        float slow = simulateFall(1.0f / 30.0f);
        float fast = simulateFall(1.0f / 144.0f);
        assertTrue(slow < DROP_HEIGHT - 1.0f, "the body never fell, it stayed at " + slow);
        assertEquals(slow, fast, TOLERANCE,
                "physics still depends on the frame rate: " + slow + " at 30 fps, " + fast + " at 144 fps");
    }

    private static float simulateFall(float frameSeconds) {
        PhysicsSystem physics = new PhysicsSystem();
        physics.initialize(null);
        physics.setFixedTimestepHertz(60);
        Scene scene = new Scene("fixed-timestep");
        scene.addGameObject(floor());
        GameObject falling = new GameObject("falling");
        falling.addComponent(new Transform3D().setPosition(0.0f, DROP_HEIGHT, 0.0f));
        falling.addComponent(new RigidBodyComponent());
        falling.addComponent(new BoxCollider());
        scene.addGameObject(falling);
        scene.advanceTick();

        int frames = Math.round(SIMULATED_SECONDS / frameSeconds);
        for (int frame = 0; frame < frames; frame++) {
            physics.update(scene, null, frameSeconds);
        }
        return falling.getComponent(Transform3D.class).orElseThrow().position().y();
    }

    private static GameObject floor() {
        GameObject ground = new GameObject("floor");
        ground.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 0.0f));
        BoxCollider shape = new BoxCollider();
        shape.halfExtents().set(20.0f, 0.5f, 20.0f);
        ground.addComponent(shape);
        return ground;
    }
}
