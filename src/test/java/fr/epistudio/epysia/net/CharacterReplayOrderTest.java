package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.prediction.CharacterInputMapper;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CharacterReplayOrderTest {
    private static final float STEP = 1.0f / 60.0f;
    private static final int FORWARD_ACTION = 0;
    private static final int JUMP_ACTION = 1;
    private static final int ACTION_COUNT = 2;
    private static final int SETTLE_TICKS = 40;
    private static final int WALK_TICKS = 30;
    private static final float SPAWN_HEIGHT = 1.5f;
    private static final float TOLERANCE = 0.002f;

    private static final CharacterInputMapper MOTION = (controller, input, delta) -> {
        float speed = input.isDown(FORWARD_ACTION) ? controller.moveSpeed() : 0.0f;
        controller.setDesiredHorizontalMove(new Vector3f(0.0f, 0.0f, -speed));
        if (input.wasPressed(JUMP_ACTION)) {
            controller.requestJump();
        }
    };

    @Test
    void replayingAWalkLandsWhereTheLiveSimulationLanded() {
        List<InputSample> walk = walkForward();
        Vector3f live = runLiveOrder(walk);
        Vector3f replayed = runReplayOrder(walk);
        assertEquals(live.z, replayed.z, TOLERANCE,
                "replay drifted from the live simulation over " + walk.size()
                        + " ticks: live z=" + live.z + " replay z=" + replayed.z);
    }

    @Test
    void replayingAJumpReachesTheSameHeightAsTheLiveSimulation() {
        List<InputSample> jump = jumpThenFall();
        Vector3f live = runLiveOrder(jump);
        Vector3f replayed = runReplayOrder(jump);
        assertEquals(live.y, replayed.y, TOLERANCE,
                "replay lost the jump: live y=" + live.y + " replay y=" + replayed.y);
    }

    private static List<InputSample> walkForward() {
        List<InputSample> samples = new ArrayList<>();
        for (int tick = 0; tick < WALK_TICKS; tick++) {
            samples.add(new InputSample(tick, 1L << FORWARD_ACTION, 0L, 0.0f, 0.0f, new float[ACTION_COUNT]));
        }
        return samples;
    }

    private static List<InputSample> jumpThenFall() {
        List<InputSample> samples = new ArrayList<>();
        long jumpMask = 1L << JUMP_ACTION;
        samples.add(new InputSample(0, jumpMask, jumpMask, 0.0f, 0.0f, new float[ACTION_COUNT]));
        for (int tick = 1; tick < WALK_TICKS; tick++) {
            samples.add(new InputSample(tick, 0L, 0L, 0.0f, 0.0f, new float[ACTION_COUNT]));
        }
        return samples;
    }

    private static Vector3f runLiveOrder(List<InputSample> samples) {
        World world = new World();
        for (InputSample sample : samples) {
            world.step();
            MOTION.applyTo(world.controller, sample, STEP);
        }
        world.step();
        return new Vector3f(world.transform.position());
    }

    private static Vector3f runReplayOrder(List<InputSample> samples) {
        World world = new World();
        for (InputSample sample : samples) {
            MOTION.applyTo(world.controller, sample, STEP);
            world.step();
        }
        return new Vector3f(world.transform.position());
    }

    private static final class World {
        private final PhysicsSystem physics = new PhysicsSystem();
        private final Scene scene = new Scene("replay-order");
        private final GameObject player = new GameObject("player");
        private final Transform3D transform = new Transform3D().setPosition(0.0f, SPAWN_HEIGHT, 0.0f);
        private final CharacterControllerComponent controller = new CharacterControllerComponent();

        private World() {
            physics.initialize(null);
            physics.setFixedTimestepHertz(60);
            scene.addGameObject(ground());
            player.addComponent(transform);
            player.addComponent(controller);
            scene.addGameObject(player);
            scene.advanceTick();
            settle();
        }

        private void settle() {
            for (int tick = 0; tick < SETTLE_TICKS; tick++) {
                physics.update(scene, null, STEP);
            }
        }

        private void step() {
            physics.stepCharacter(player, STEP);
        }

        private static GameObject ground() {
            GameObject floor = new GameObject("floor");
            floor.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 0.0f));
            BoxCollider shape = new BoxCollider();
            shape.halfExtents().set(50.0f, 0.5f, 50.0f);
            floor.addComponent(shape);
            return floor;
        }
    }
}
