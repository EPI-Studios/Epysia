package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CharacterControllerComponent;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.PhysicsEventListener;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterTriggerTest {

    private static final float STEP = 1.0f / 60.0f;

    private PhysicsSystem physics;
    private Scene scene;
    private GameObject player;
    private Transform3D playerTransform;
    private CharacterControllerComponent controller;
    private Recorder playerEvents;

    @BeforeEach
    void startWorld() {
        physics = new PhysicsSystem();
        physics.initialize(null);
        scene = new Scene("triggers");
        addGround();
        player = new GameObject("player");
        playerTransform = player.addComponent(new Transform3D().setPosition(0.0f, 0.5f, 0.0f));
        controller = player.addComponent(new CharacterControllerComponent().setCapsule(0.3f, 0.2f));
        playerEvents = player.addComponent(new Recorder());
        scene.addGameObject(player);
    }

    @AfterEach
    void stopWorld() {
        physics.shutdown();
    }

    @Test
    void aCharacterWalksThroughATriggerInsteadOfBeingStoppedByIt() {
        Recorder zone = addTrigger(new Vector3f(2.0f, 0.5f, 0.0f));

        walkForward(60);

        assertTrue(zone.entered.contains(player), "the trigger must report the character entering it");
        assertTrue(playerTransform.worldPosition(new Vector3f()).x > 2.0f,
                "the character must reach the middle of the trigger, a trigger is not a wall");
    }

    @Test
    void aTriggerParentedToAMovingObjectSitsWhereItLooks() {
        GameObject holder = new GameObject("holder");
        Transform3D holderTransform = holder.addComponent(new Transform3D().setPosition(2.0f, 0.5f, -8.0f));
        scene.addGameObject(holder);
        GameObject zone = new GameObject("zone");
        Transform3D zoneTransform = zone.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 8.0f));
        zoneTransform.setParent(holderTransform);
        zone.addComponent(new BoxCollider().setHalfExtents(0.5f, 0.5f, 0.5f).setTrigger(true));
        Recorder events = zone.addComponent(new Recorder());
        scene.addGameObject(zone);

        walkForward(60);

        assertTrue(events.entered.contains(player),
                "a child collider must follow its parent, not sit at the world origin");
    }

    @Test
    void aColliderParentedToAWalkingCharacterWalksWithIt() {
        GameObject head = new GameObject("head");
        Transform3D headTransform = head.addComponent(new Transform3D().setPosition(0.0f, 2.0f, 0.0f));
        headTransform.setParent(playerTransform);
        head.addComponent(new BoxCollider().setHalfExtents(0.4f, 0.4f, 0.4f));
        scene.addGameObject(head);

        walkForward(90);

        float travelledX = headTransform.worldPosition(new Vector3f()).x;
        assertTrue(travelledX > 2.0f, "the child transform must follow the character");
        assertTrue(colliderStandsAt(travelledX), "the child collider must follow the character");
        assertTrue(!colliderStandsAt(0.0f), "the child collider must not stay where it spawned");
    }

    private boolean colliderStandsAt(float x) {
        return physics.raycast(new Vector3f(x, 6.0f, 0.0f), new Vector3f(0.0f, -1.0f, 0.0f), 4.0f)
                .isPresent();
    }

    @Test
    void aCharacterReportsWhatItStandsOn() {
        walkForward(10);

        assertTrue(playerEvents.collided.stream().anyMatch(other -> "ground".equals(other.name())),
                "a character controller must report its contacts as collision events");
    }

    @Test
    void movingTheTransformMovesTheCharacterBody() {
        controller.setApplyGravity(false);
        walkForward(5);

        playerTransform.setPosition(5.0f, 0.5f, 0.0f);
        step();

        Vector3fc bodyPosition = physics.world().getBodyPose(controller.bodyHandle()).position();
        assertEquals(5.0f, bodyPosition.x(), 0.05f,
                "the physics body must follow a transform the script moved by hand");
    }

    private Recorder addTrigger(Vector3f position) {
        GameObject zone = new GameObject("zone");
        zone.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        zone.addComponent(new BoxCollider().setHalfExtents(0.5f, 0.5f, 0.5f).setTrigger(true));
        Recorder events = zone.addComponent(new Recorder());
        scene.addGameObject(zone);
        return events;
    }

    private void addGround() {
        GameObject ground = new GameObject("ground");
        ground.addComponent(new Transform3D().setPosition(0.0f, -0.5f, 0.0f));
        ground.addComponent(new RigidBodyComponent()).setKind(RigidBodyKind.STATIC);
        ground.addComponent(new BoxCollider().setHalfExtents(20.0f, 0.5f, 20.0f));
        scene.addGameObject(ground);
    }

    private void walkForward(int steps) {
        for (int index = 0; index < steps; index++) {
            controller.move(new Vector3f(5.0f, 0.0f, 0.0f));
            step();
        }
    }

    private void step() {
        scene.advanceTick();
        physics.update(scene, null, STEP);
    }

    private static final class Recorder extends Component implements PhysicsEventListener {
        private final List<GameObject> entered = new ArrayList<>();
        private final List<GameObject> collided = new ArrayList<>();

        @Override
        public void onTriggerEnter(GameObject other) {
            entered.add(other);
        }

        @Override
        public void onCollision(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
            collided.add(other);
        }
    }
}
