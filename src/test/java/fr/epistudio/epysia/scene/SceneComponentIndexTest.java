package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneComponentIndexTest {

    @Test
    void seesObjectsAddedAfterAQuery() {
        Scene scene = new Scene("indexed");
        scene.addGameObject(objectWithRenderer("first"));
        scene.advanceTick();
        assertEquals(1, scene.componentsOf(MeshRenderer.class).size());

        scene.addGameObject(objectWithRenderer("second"));
        scene.advanceTick();

        assertEquals(2, scene.componentsOf(MeshRenderer.class).size(),
                "an object added after the index was built must appear");
    }

    @Test
    void dropsObjectsRemovedAfterAQuery() {
        Scene scene = new Scene("indexed");
        GameObject doomed = objectWithRenderer("doomed");
        scene.addGameObject(objectWithRenderer("kept"));
        scene.addGameObject(doomed);
        scene.advanceTick();
        assertEquals(2, scene.componentsOf(MeshRenderer.class).size());

        scene.removeGameObject(doomed);
        scene.advanceTick();

        List<MeshRenderer> remaining = scene.componentsOf(MeshRenderer.class);
        assertEquals(1, remaining.size(), "a removed object must leave the index");
        assertEquals("kept", remaining.get(0).ownerOrNull().name());
    }

    @Test
    void ignoresAnObjectAddedAndRemovedBeforeTheNextQuery() {
        Scene scene = new Scene("indexed");
        scene.addGameObject(objectWithRenderer("kept"));
        scene.advanceTick();
        assertEquals(1, scene.componentsOf(MeshRenderer.class).size());

        GameObject transient1 = objectWithRenderer("transient");
        scene.addGameObject(transient1);
        scene.advanceTick();
        scene.removeGameObject(transient1);
        scene.advanceTick();

        assertEquals(1, scene.componentsOf(MeshRenderer.class).size(),
                "an object that came and went must not linger in the index");
    }

    @Test
    void seesAComponentAddedToAnObjectAlreadyInTheScene() {
        Scene scene = new Scene("indexed");
        GameObject host = objectWithRenderer("host");
        scene.addGameObject(host);
        scene.advanceTick();
        assertEquals(0, scene.componentsOf(PointLight.class).size());

        host.addComponent(new PointLight());

        assertEquals(1, scene.componentsOf(PointLight.class).size(),
                "a component added later must appear in its index");
        assertEquals(1, scene.componentsOf(MeshRenderer.class).size(),
                "the other indexes must not gain duplicates");
    }

    @Test
    void keepsIndexesConsistentUnderRepeatedStreaming() {
        Scene scene = new Scene("indexed");
        List<GameObject> live = new java.util.ArrayList<>();
        for (int round = 0; round < 40; round++) {
            GameObject added = objectWithRenderer("chunk" + round);
            live.add(added);
            scene.addGameObject(added);
            if (live.size() > 8) {
                scene.removeGameObject(live.remove(0));
            }
            scene.advanceTick();
            assertEquals(live.size(), scene.componentsOf(MeshRenderer.class).size(),
                    "index size must track the live set at round " + round);
        }
        assertTrue(scene.componentsOf(Transform3D.class).size() == live.size(),
                "every live object must still carry its transform in the index");
    }

    private static GameObject objectWithRenderer(String name) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D());
        object.addComponent(new MeshRenderer());
        return object;
    }
}
