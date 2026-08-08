package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.HingeJoint;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HingeJointTest {
    private static final float FRAME_SECONDS = 1.0f / 60.0f;
    private static final float START_HEIGHT = 5.0f;
    private static final int FRAMES = 240;

    @Test
    void aHingedBodyStaysNearItsAnchorInsteadOfFallingAway() {
        Scene scene = new Scene("hinge");
        GameObject door = new GameObject("door");
        door.addComponent(new Transform3D().setPosition(0.0f, START_HEIGHT, 0.0f));
        door.addComponent(new RigidBodyComponent());
        door.addComponent(new BoxCollider());
        HingeJoint hinge = new HingeJoint();
        hinge.anchor().set(0.0f, 0.5f, 0.0f);
        door.addComponent(hinge);
        scene.addGameObject(door);
        scene.advanceTick();

        PhysicsSystem physics = new PhysicsSystem();
        physics.initialize(null);
        physics.setFixedTimestepHertz(60);
        for (int frame = 0; frame < FRAMES; frame++) {
            physics.update(scene, null, FRAME_SECONDS);
        }

        assertTrue(hinge.isRegistered(), "the hinge never registered against the world anchor");
        Vector3f position = door.getComponent(Transform3D.class).orElseThrow().position();
        assertTrue(position.y() > START_HEIGHT - 2.0f,
                "the hinge did not hold the body, it fell to " + position.y());
    }
}
