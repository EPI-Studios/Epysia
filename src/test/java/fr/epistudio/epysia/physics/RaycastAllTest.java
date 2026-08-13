package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.RaycastHit;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycastAllTest {

    private PhysicsSystem physics;
    private Scene scene;

    @BeforeEach
    void startWorld() {
        physics = new PhysicsSystem();
        physics.initialize(null);
        scene = new Scene("raycast");
    }

    @AfterEach
    void stopWorld() {
        physics.shutdown();
    }

    @Test
    void everyWallOnTheLineIsReturnedNearestFirst() {
        addWall("near", 2.0f);
        addWall("middle", 5.0f);
        addWall("far", 9.0f);
        step();

        List<RaycastHit> hits = physics.raycastAll(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f);

        assertEquals(3, hits.size(), "a ray through three walls must report three hits");
        assertTrue(hits.get(0).distance() < hits.get(1).distance(),
                "hits must be sorted nearest first");
        assertTrue(hits.get(1).distance() < hits.get(2).distance(),
                "hits must be sorted nearest first");
    }

    @Test
    void theSingleHitRaycastStillReturnsTheNearest() {
        addWall("near", 2.0f);
        addWall("far", 9.0f);
        step();

        RaycastHit closest = physics.raycast(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f).orElseThrow();
        List<RaycastHit> all = physics.raycastAll(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f);

        assertEquals(closest.distance(), all.get(0).distance(), 1.0e-3f,
                "the multi hit list must start with what the single hit call returns");
    }

    @Test
    void aBodyIsNeverReportedTwice() {
        addWall("only", 3.0f);
        step();

        List<RaycastHit> hits = physics.raycastAll(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f);

        assertEquals(1, hits.size(),
                "walking the ray past a hit must not report the same body on the way out");
    }

    @Test
    void theHitBudgetIsRespected() {
        addWall("a", 2.0f);
        addWall("b", 4.0f);
        addWall("c", 6.0f);
        step();

        List<RaycastHit> hits = physics.raycastAll(
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f,
                fr.epistudio.epysia.physics.api.QueryFilter.ALL, 2);

        assertEquals(2, hits.size(), "the caller's budget bounds the result");
    }

    @Test
    void aRayThatHitsNothingReturnsAnEmptyList() {
        addWall("aside", 3.0f);
        step();

        List<RaycastHit> hits = physics.raycastAll(
                new Vector3f(0.0f, 50.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), 20.0f);

        assertTrue(hits.isEmpty(), "a miss is an empty list, never a null");
    }

    private void addWall(String name, float z) {
        GameObject wall = new GameObject(name);
        wall.addComponent(new Transform3D().setPosition(0.0f, 0.0f, z));
        RigidBodyComponent body = wall.addComponent(new RigidBodyComponent());
        body.setKind(RigidBodyKind.STATIC);
        wall.addComponent(new BoxCollider().setHalfExtents(4.0f, 4.0f, 0.25f));
        scene.addGameObject(wall);
    }

    private void step() {
        scene.advanceTick();
        physics.update(scene, null, 1.0f / 60.0f);
    }
}
