package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LightRoundTripTest {

    private static final float RANGE = 42.0f;
    private static final float INNER_DEGREES = 7.0f;
    private static final float OUTER_DEGREES = 31.0f;
    private static final int RENDER_LAYER = 3;
    private static final float COSINE_TOLERANCE = 1.0e-5f;

    @Test
    void keepsSpotRangeAndConeThroughSerialization() {
        GameObject restored = roundTrip();
        SpotLight light = restored.getComponentOrNull(SpotLight.class);

        assertEquals(RANGE, light.range(), "spot range must survive a scene round trip");
        assertEquals(Math.cos(Math.toRadians(INNER_DEGREES)), light.innerConeCosine(), COSINE_TOLERANCE,
                "spot inner cone must survive a scene round trip");
        assertEquals(Math.cos(Math.toRadians(OUTER_DEGREES)), light.outerConeCosine(), COSINE_TOLERANCE,
                "spot outer cone must survive a scene round trip");
    }

    @Test
    void keepsTransformVisibilityAndRenderLayerThroughSerialization() {
        Transform3D restored = roundTrip().getComponentOrNull(Transform3D.class);

        assertEquals(RENDER_LAYER, restored.renderLayer(), "render layer must survive a scene round trip");
        assertFalse(restored.visible(), "visibility must survive a scene round trip");
    }

    private static GameObject roundTrip() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene source = new Scene("source");
        source.addGameObject(buildLight());
        source.advanceTick();
        String text = serializer.serialize(source, gameObject -> true);
        Scene target = new Scene("target");
        serializer.deserialize(target, text, null);
        target.advanceTick();
        return target.gameObjects().get(0);
    }

    private static GameObject buildLight() {
        GameObject object = new GameObject("spot");
        object.addComponent(new Transform3D().setRenderLayer(RENDER_LAYER).setVisible(false));
        object.addComponent(new SpotLight().setRange(RANGE).setConeDegrees(INNER_DEGREES, OUTER_DEGREES));
        return object;
    }
}
