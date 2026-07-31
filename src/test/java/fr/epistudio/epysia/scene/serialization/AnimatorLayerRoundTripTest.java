package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.animation.AnimationBlendMode;
import fr.epistudio.epysia.animation.AnimationLayer;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimatorLayerRoundTripTest {

    private static final float AIM_WEIGHT = 0.75f;
    private static final String AIM_CLIP = "clips/aim.epyclip";
    private static final String MASK_ROOT = "spine";

    @Test
    void keepsEveryLayerThroughSerialization() {
        List<AnimationLayer> restored = roundTrip().layers();

        assertEquals(2, restored.size(), "both layers must survive a scene round trip");
    }

    @Test
    void keepsLayerSettingsThroughSerialization() {
        AnimationLayer restored = roundTrip().layers().get(0);

        assertEquals(AIM_CLIP, restored.clipPath(), "layer clip path must survive");
        assertEquals(AnimationBlendMode.ADDITIVE, restored.blendMode(), "layer blend mode must survive");
        assertEquals(AIM_WEIGHT, restored.weight(), 1.0e-6f, "layer weight must survive");
        assertEquals(MASK_ROOT, restored.maskRootJoint(), "layer mask root must survive");
    }

    @Test
    void keepsTheOrderOfLayers() {
        List<AnimationLayer> restored = roundTrip().layers();

        assertEquals(AnimationBlendMode.OVERRIDE, restored.get(1).blendMode(),
                "the second layer must stay second");
    }

    private static Animator roundTrip() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene source = new Scene("source");
        source.addGameObject(buildAnimated());
        source.advanceTick();
        String text = serializer.serialize(source, gameObject -> true);
        Scene target = new Scene("target");
        serializer.deserialize(target, text, null);
        target.advanceTick();
        return target.gameObjects().get(0).getComponentOrNull(Animator.class);
    }

    private static GameObject buildAnimated() {
        GameObject object = new GameObject("character");
        object.addComponent(new Transform3D());
        Animator animator = object.addComponent(new Animator());
        animator.addLayer()
                .setClipPath(AIM_CLIP)
                .setBlendMode(AnimationBlendMode.ADDITIVE)
                .setWeight(AIM_WEIGHT)
                .setMaskRootJoint(MASK_ROOT);
        animator.addLayer().setBlendMode(AnimationBlendMode.OVERRIDE);
        return object;
    }
}
