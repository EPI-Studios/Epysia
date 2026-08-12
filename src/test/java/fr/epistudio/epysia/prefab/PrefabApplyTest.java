package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabApplyTest {

    private static final String PREFAB_PATH = "res://prefabs/lamp.epyprefab";
    private static final float TOLERANCE = 1.0e-4f;

    @Test
    void aWrittenPrefabDoesNotDeclareItselfAnInstanceOfItself(@TempDir Path directory)
            throws IOException {
        Path prefabFile = directory.resolve("lamp.epyprefab");
        GameObject instance = instantiate(new Scene("scene"));

        newApplier().applyToPrefab(instance, prefabFile);

        String written = Files.readString(prefabFile);
        assertFalse(written.contains("prefabSource"),
                "a prefab file must not carry a link back to itself");
        assertFalse(written.contains("prefabOverrides"),
                "a prefab file must not carry the instance's override marks");
    }

    @Test
    void applyingWritesTheInstanceValuesAndClearsTheOverrides(@TempDir Path directory)
            throws IOException {
        Path prefabFile = directory.resolve("lamp.epyprefab");
        GameObject instance = instantiate(new Scene("scene"));
        instance.getComponentOrNull(PointLight.class).setRange(42.0f);
        instance.markOverridden(PointLight.class, "range");

        newApplier().applyToPrefab(instance, prefabFile);

        assertTrue(Files.readString(prefabFile).contains("42"),
                "the edited value must reach the prefab file");
        assertTrue(instance.overriddenProperties().isEmpty(),
                "applying makes the instance match its prefab, so nothing is overridden any more");
        assertEquals(42.0f, instance.getComponentOrNull(PointLight.class).range(), TOLERANCE,
                "applying must not disturb the instance itself");
    }

    private static PrefabApplier newApplier() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new PrefabApplier(registry);
    }

    private static GameObject instantiate(Scene scene) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        return new PrefabInstantiator(registry).instantiate("""
                {"name":"lamp","gameObjects":[{"name":"lamp","active":true,"parentIndex":-1,
                "components":[
                {"type":"fr.epistudio.epysia.components.transforms.Transform3D",
                 "displayName":"Transform 3D","fields":{}},
                {"type":"fr.epistudio.epysia.components.PointLight",
                 "displayName":"Point Light","fields":{"range":12.0}}]}]}
                """, scene, null, PREFAB_PATH);
    }
}
