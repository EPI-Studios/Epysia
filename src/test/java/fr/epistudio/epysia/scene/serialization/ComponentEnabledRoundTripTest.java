package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.PointLight2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentEnabledRoundTripTest {

    @Test
    void aDisabledComponentStaysDisabledAfterAReload() {
        GameObject source = new GameObject("light");
        source.addComponent(new Transform3D());
        source.addComponent(new PointLight()).setEnabled(false);

        GameObject restored = roundTrip(source);

        assertFalse(restored.getComponentOrNull(PointLight.class).enabled(),
                "a component disabled in the editor must reload disabled");
    }

    @Test
    void anEnabledComponentStaysEnabledAfterAReload() {
        GameObject source = new GameObject("light");
        source.addComponent(new Transform3D());
        source.addComponent(new PointLight());

        GameObject restored = roundTrip(source);

        assertTrue(restored.getComponentOrNull(PointLight.class).enabled(),
                "a component left enabled must reload enabled");
    }

    @Test
    void aLegacyLight2dEnabledFieldStillDisablesTheComponent() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene target = new Scene("target");

        serializer.deserialize(target, legacyLight2dScene(), null);
        target.advanceTick();

        PointLight2D restored = target.gameObjects().getFirst()
                .getComponentOrNull(PointLight2D.class);
        assertFalse(restored.enabled(),
                "a scene written before the component flag existed must keep its light switched off");
    }

    private static String legacyLight2dScene() {
        return """
                {"name":"target","gameObjects":[{"id":"11111111-1111-1111-1111-111111111111",
                "name":"legacy","tag":"","active":true,"parentIndex":-1,
                "components":[{"type":"fr.epistudio.epysia.components.PointLight2D",
                "displayName":"Point Light 2D","fields":{"enabled":false}}]}]}
                """;
    }

    private static GameObject roundTrip(GameObject source) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene origin = new Scene("source");
        origin.addGameObject(source);
        origin.advanceTick();
        String text = serializer.serialize(origin, gameObject -> true);
        Scene target = new Scene("target");
        serializer.deserialize(target, text, null);
        target.advanceTick();
        return target.gameObjects().getFirst();
    }
}
