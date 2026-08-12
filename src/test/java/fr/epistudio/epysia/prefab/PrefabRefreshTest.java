package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabRefreshTest {

    private static final String PREFAB_PATH = "res://prefabs/lamp.epyprefab";
    private static final float AUTHORED_RANGE = 12.0f;
    private static final float EDITED_RANGE = 25.0f;
    private static final float TOLERANCE = 1.0e-4f;

    @Test
    void anEditToThePrefabReachesTheInstance() {
        Scene scene = new Scene("scene");
        GameObject instance = instantiate(scene, prefabWithRange(AUTHORED_RANGE));

        refresherFor(prefabWithRange(EDITED_RANGE)).refresh(scene);

        assertEquals(EDITED_RANGE, instance.getComponentOrNull(PointLight.class).range(), TOLERANCE,
                "an edit to the prefab must reach an instance that never overrode the field");
    }

    @Test
    void anOverriddenFieldIgnoresThePrefab() {
        Scene scene = new Scene("scene");
        GameObject instance = instantiate(scene, prefabWithRange(AUTHORED_RANGE));
        instance.getComponentOrNull(PointLight.class).setRange(99.0f);
        instance.markOverridden(PointLight.class, "range");

        refresherFor(prefabWithRange(EDITED_RANGE)).refresh(scene);

        assertEquals(99.0f, instance.getComponentOrNull(PointLight.class).range(), TOLERANCE,
                "a field the instance overrode must survive a prefab edit");
    }

    @Test
    void revertingAPropertyLetsThePrefabWinAgain() {
        Scene scene = new Scene("scene");
        GameObject instance = instantiate(scene, prefabWithRange(AUTHORED_RANGE));
        instance.getComponentOrNull(PointLight.class).setRange(99.0f);
        instance.markOverridden(PointLight.class, "range");
        PrefabRefresher refresher = refresherFor(prefabWithRange(EDITED_RANGE));

        refresher.revertProperty(instance, PointLight.class, "range");

        assertEquals(EDITED_RANGE, instance.getComponentOrNull(PointLight.class).range(), TOLERANCE,
                "reverting a property must take the prefab value again");
        assertFalse(instance.isOverridden(PointLight.class, "range"),
                "reverting must clear the override mark");
    }

    @Test
    void anInstanceRemembersItsPrefabAndObjectIndex() {
        Scene scene = new Scene("scene");
        GameObject instance = instantiate(scene, prefabWithRange(AUTHORED_RANGE));

        assertTrue(instance.isPrefabInstance(), "an instantiated prefab must know it is one");
        assertEquals(PREFAB_PATH, instance.prefabSource(), "the instance must record its prefab");
        assertEquals(0, instance.prefabObjectId(), "the root is the first object of the prefab");
    }

    @Test
    void aMissingPrefabLeavesTheInstanceUntouched() {
        Scene scene = new Scene("scene");
        GameObject instance = instantiate(scene, prefabWithRange(AUTHORED_RANGE));

        new PrefabRefresher(source -> Optional.empty(), newSerializer()::applyFields).refresh(scene);

        assertEquals(AUTHORED_RANGE, instance.getComponentOrNull(PointLight.class).range(), TOLERANCE,
                "a prefab that cannot be read must leave the expanded objects alone");
    }

    private static GameObject instantiate(Scene scene, String prefabText) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new PrefabInstantiator(registry)
                .instantiate(prefabText, scene, null, PREFAB_PATH);
    }

    private static PrefabRefresher refresherFor(String prefabText) {
        return new PrefabRefresher(source -> Optional.of(prefabText), newSerializer()::applyFields);
    }

    private static SceneSerializer newSerializer() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new SceneSerializer(registry);
    }

    private static String prefabWithRange(float range) {
        return """
                {"name":"lamp","gameObjects":[{"name":"lamp","active":true,"parentIndex":-1,
                "components":[
                {"type":"fr.epistudio.epysia.components.transforms.Transform3D",
                 "displayName":"Transform 3D","fields":{}},
                {"type":"fr.epistudio.epysia.components.PointLight",
                 "displayName":"Point Light","fields":{"range":%s}}]}]}
                """.formatted(range);
    }
}
