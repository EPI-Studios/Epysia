package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneProvenanceTest {

    private static final String FIRST_LEVEL = "res://levels/first.epyscene";
    private static final String SECOND_LEVEL = "res://levels/second.epyscene";

    @Test
    void anAdditiveLoadKeepsWhatWasAlreadyThere() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();
        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);

        serializer.deserializeInto(scene, sceneWith("beta"), null, SceneLoadMode.ADDITIVE, SECOND_LEVEL);

        assertEquals(2, scene.gameObjects().size(), "an additive load must add rather than replace");
        assertTrue(scene.findByName("alpha").isPresent(), "the first level must survive an additive load");
        assertTrue(scene.findByName("beta").isPresent(), "the second level must be present too");
    }

    @Test
    void aReplacingLoadRemovesTheEarlierLevel() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();
        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);

        serializer.deserializeInto(scene, sceneWith("beta"), null, SceneLoadMode.REPLACE, SECOND_LEVEL);

        assertEquals(1, scene.gameObjects().size(), "a replacing load must clear the previous level");
        assertFalse(scene.findByName("alpha").isPresent(), "the earlier level must be gone");
    }

    @Test
    void anObjectMarkedToSurviveOutlivesASceneChange() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();
        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);
        GameObject player = new GameObject("player");
        player.addComponent(new Transform3D());
        player.setKeepOnSceneChange(true);
        scene.addGameObject(player);
        scene.advanceTick();

        serializer.deserializeInto(scene, sceneWith("beta"), null, SceneLoadMode.REPLACE, SECOND_LEVEL);

        assertTrue(scene.findByName("player").isPresent(),
                "an object marked to survive must outlive a scene change");
        assertTrue(player.isAlive(), "the surviving object must not be marked destroyed");
    }

    @Test
    void unloadingASourceRemovesOnlyThatLevel() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();
        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);
        serializer.deserializeInto(scene, sceneWith("beta"), null, SceneLoadMode.ADDITIVE, SECOND_LEVEL);

        serializer.unloadSource(scene, FIRST_LEVEL);

        assertFalse(scene.findByName("alpha").isPresent(), "unloading a source must remove its objects");
        assertTrue(scene.findByName("beta").isPresent(), "unloading one source must leave the other alone");
    }

    @Test
    void everyLoadedObjectRemembersWhereItCameFrom() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();

        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);

        assertEquals(FIRST_LEVEL, scene.findByName("alpha").orElseThrow().sourceId(),
                "a loaded object must record the file it came from");
    }

    @Test
    void aRuntimeObjectHasNoSourceAndSurvivesUnloadingEveryLevel() {
        Scene scene = new Scene("scene");
        SceneSerializer serializer = newSerializer();
        serializer.deserializeInto(scene, sceneWith("alpha"), null, SceneLoadMode.REPLACE, FIRST_LEVEL);
        GameObject spawned = new GameObject("spawned");
        spawned.addComponent(new Transform3D());
        scene.addGameObject(spawned);
        scene.advanceTick();

        serializer.unloadSource(scene, FIRST_LEVEL);

        assertEquals("", spawned.sourceId(), "an object created at runtime belongs to no scene file");
        assertTrue(scene.findByName("spawned").isPresent(),
                "unloading a level must not remove objects that level never created");
    }

    private static SceneSerializer newSerializer() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new SceneSerializer(registry);
    }

    private static String sceneWith(String objectName) {
        return """
                {"name":"level","gameObjects":[{"name":"%s","active":true,"parentIndex":-1,
                "components":[{"type":"fr.epistudio.epysia.components.transforms.Transform3D",
                "displayName":"Transform 3D","fields":{}}]}]}
                """.formatted(objectName);
    }
}
