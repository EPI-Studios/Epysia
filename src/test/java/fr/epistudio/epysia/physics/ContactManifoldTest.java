package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.ContactEvent;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.scripting.Behaviour;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactManifoldTest {

    private PhysicsSystem physics;
    private Scene scene;

    @BeforeEach
    void startWorld() {
        physics = new PhysicsSystem();
        physics.initialize(null);
        scene = new Scene("contacts");
    }

    @AfterEach
    void stopWorld() {
        physics.shutdown();
    }

    @Test
    void anOrdinaryLandingReportsWhereItTouched() {
        addGround();
        addFallingBox(1.2f);

        List<ContactEvent> started = runUntilFirstContact();

        assertFalse(started.isEmpty(), "a box dropped onto the ground must produce a contact");
        ContactEvent contact = started.getFirst();
        assertFalse(isZero(contact.normal()),
                "an ordinary collision must carry a real normal, not the zero vector it used to");
        assertTrue(contact.normal().y() > 0.5f || contact.normal().y() < -0.5f,
                "landing flat on the ground gives a mostly vertical normal, got " + contact.normal());
    }

    @Test
    void theContactPointSitsBetweenTheTwoBodies() {
        addGround();
        addFallingBox(1.2f);

        List<ContactEvent> started = runUntilFirstContact();

        assertFalse(started.isEmpty(), "a contact is needed for this to mean anything");
        ContactEvent contact = started.getFirst();
        assertFalse(isZero(contact.point()),
                "an ordinary collision must carry a real point, not the origin");
        assertTrue(Math.abs(contact.point().y()) < 3.0f,
                "the touch point is near the ground plane, got y " + contact.point().y());
    }

    @EpysiaComponent(name = "Contact Recorder", category = "Testing")
    public static final class ContactRecorder extends Behaviour {

        final List<Vector3f> points = new ArrayList<>();
        final List<Vector3f> normals = new ArrayList<>();

        @Override
        public void onCollision(GameObject other, Vector3fc point, Vector3fc normal, float approachSpeed) {
            points.add(new Vector3f(point));
            normals.add(new Vector3f(normal));
        }
    }

    private ContactRecorder recorder;

    private List<ContactEvent> runUntilFirstContact() {
        for (int step = 0; step < 240 && recorder.points.isEmpty(); step++) {
            scene.advanceTick();
            physics.update(scene, null, 1.0f / 60.0f);
        }
        List<ContactEvent> started = new ArrayList<>();
        for (int index = 0; index < recorder.points.size(); index++) {
            started.add(new ContactEvent(null, null, recorder.points.get(index),
                    recorder.normals.get(index), 0.0f, true));
        }
        return started;
    }

    private void addGround() {
        GameObject ground = new GameObject("ground");
        ground.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 0.0f));
        RigidBodyComponent body = ground.addComponent(new RigidBodyComponent());
        body.setKind(RigidBodyKind.STATIC);
        ground.addComponent(new BoxCollider().setHalfExtents(10.0f, 0.5f, 10.0f));
        scene.addGameObject(ground);
    }

    private void addFallingBox(float height) {
        GameObject box = new GameObject("box");
        box.addComponent(new Transform3D().setPosition(0.0f, height, 0.0f));
        box.addComponent(new RigidBodyComponent());
        box.addComponent(new BoxCollider().setHalfExtents(0.5f, 0.5f, 0.5f));
        recorder = box.addComponent(new ContactRecorder());
        scene.addGameObject(box);
    }

    private static boolean isZero(org.joml.Vector3fc vector) {
        return vector.lengthSquared() < 1.0e-8f;
    }
}
