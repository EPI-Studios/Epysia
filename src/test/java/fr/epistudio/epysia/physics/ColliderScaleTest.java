package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColliderScaleTest {

    private PhysicsSystem physics;
    private Scene scene;
    private GameObject wall;
    private Transform3D transform;
    private BoxCollider collider;

    @BeforeEach
    void startWorld() {
        physics = new PhysicsSystem();
        physics.initialize(null);
        scene = new Scene("scaling");
        wall = new GameObject("wall");
        transform = wall.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 5.0f));
        RigidBodyComponent body = wall.addComponent(new RigidBodyComponent());
        body.setKind(RigidBodyKind.STATIC);
        collider = wall.addComponent(new BoxCollider().setHalfExtents(1.0f, 1.0f, 0.25f));
        scene.addGameObject(wall);
        step();
    }

    @AfterEach
    void stopWorld() {
        physics.shutdown();
    }

    @Test
    void changingTheTransformScaleAsksForARebuild() {
        assertFalse(collider.requiresRebuild(), "a collider at its registered scale is settled");

        transform.setScale(4.0f, 4.0f, 1.0f);

        assertTrue(collider.requiresRebuild(),
                "a scale change must ask for a rebuild, otherwise the shape stays the old size");
    }

    @Test
    void aRebuiltColliderCoversItsNewSize() {
        Vector3f origin = new Vector3f(3.0f, 0.0f, 0.0f);
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        assertTrue(physics.raycast(origin, forward, 20.0f).isEmpty(),
                "the ray starts outside the unscaled box");

        transform.setScale(6.0f, 6.0f, 1.0f);
        step();

        assertTrue(physics.raycast(origin, forward, 20.0f).isPresent(),
                "after scaling up, the same ray must hit the collider");
    }

    @Test
    void scalingBackDownShrinksTheColliderAgain() {
        Vector3f origin = new Vector3f(3.0f, 0.0f, 0.0f);
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        transform.setScale(6.0f, 6.0f, 1.0f);
        step();
        assertTrue(physics.raycast(origin, forward, 20.0f).isPresent(), "scaled up, the ray hits");

        transform.setScale(1.0f, 1.0f, 1.0f);
        step();

        assertTrue(physics.raycast(origin, forward, 20.0f).isEmpty(),
                "scaling back down must shrink the shape, not leave it large");
    }

    @Test
    void aSettledColliderDoesNotRebuildEveryFrame() {
        step();
        step();

        assertFalse(collider.requiresRebuild(),
                "an unchanged collider must not ask for a rebuild, that would thrash the world");
    }

    private void step() {
        scene.advanceTick();
        physics.update(scene, null, 1.0f / 60.0f);
    }
}
