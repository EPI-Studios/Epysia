package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.api.RigidBodyPose;
import fr.epistudio.epysia.physics.api.ShapeCastHit;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeQueryTest {

    private static final ShapeDescriptor.Box SLAB =
            new ShapeDescriptor.Box(new Vector3f(2.0f, 0.05f, 0.05f));
    private static final ShapeDescriptor.Box QUERY_CUBE =
            new ShapeDescriptor.Box(new Vector3f(0.5f, 0.5f, 0.5f));

    private PhysicsSystem physics;
    private Scene scene;

    @BeforeEach
    void startWorld() {
        physics = new PhysicsSystem();
        physics.initialize(null);
        scene = new Scene("shape-queries");
    }

    @AfterEach
    void stopWorld() {
        physics.shutdown();
    }

    @Test
    void aSlabCastPassesUnderWhatItsBoundingSphereWouldHit() {
        addBox("above", new Vector3f(0.0f, 1.0f, 5.0f), 0.25f);
        step();

        Optional<ShapeCastHit> hit = physics.shapeCast(SLAB, atOrigin(),
                new Vector3f(0.0f, 0.0f, 1.0f), 10.0f);

        assertTrue(hit.isEmpty(),
                "the slab is 0.05 tall and passes a metre under the obstacle, "
                        + "only its 2 metre bounding sphere would touch it");
    }

    @Test
    void aSlabCastStillHitsWhatIsInFrontOfIt() {
        addBox("ahead", new Vector3f(0.0f, 0.0f, 5.0f), 0.25f);
        step();

        ShapeCastHit hit = physics.shapeCast(SLAB, atOrigin(),
                new Vector3f(0.0f, 0.0f, 1.0f), 10.0f).orElseThrow();

        assertEquals("ahead", physics.ownerOf(hit.body()).orElseThrow().name());
    }

    @Test
    void aRotatedSlabHitsWhatTheUnrotatedOneMisses() {
        addBox("beside", new Vector3f(0.0f, 1.0f, 5.0f), 0.25f);
        step();

        RigidBodyPose upright = new RigidBodyPose(new Vector3f(),
                new Quaternionf().rotateZ((float) Math.PI / 2.0f));

        assertTrue(physics.shapeCast(SLAB, upright, new Vector3f(0.0f, 0.0f, 1.0f), 10.0f).isPresent(),
                "stood on end the slab reaches a metre up, so the pose rotation must reach the query");
    }

    @Test
    void overlapIgnoresABodyInsideTheBoundingBoxButOutsideTheShape() {
        addBox("corner", new Vector3f(0.8f, 0.8f, 0.8f), 0.02f);
        addBox("inside", new Vector3f(0.0f, 0.0f, 0.0f), 0.02f);
        step();

        List<String> found = physics.overlap(QUERY_CUBE, atOrigin()).stream()
                .map(GameObject::name)
                .toList();

        assertTrue(found.contains("inside"), "a body at the centre of the query must be reported");
        assertFalse(found.contains("corner"),
                "the corner body sits inside the old bounding box but outside the half metre cube");
    }

    private static RigidBodyPose atOrigin() {
        return new RigidBodyPose(new Vector3f(), new Quaternionf());
    }

    private void addBox(String name, Vector3f position, float halfExtent) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D().setPosition(position.x, position.y, position.z));
        RigidBodyComponent body = object.addComponent(new RigidBodyComponent());
        body.setKind(RigidBodyKind.STATIC);
        object.addComponent(new BoxCollider().setHalfExtents(halfExtent, halfExtent, halfExtent));
        scene.addGameObject(object);
    }

    private void step() {
        scene.advanceTick();
        physics.update(scene, null, 1.0f / 60.0f);
    }
}
