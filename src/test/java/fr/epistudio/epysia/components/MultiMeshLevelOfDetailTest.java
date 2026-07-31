package fr.epistudio.epysia.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiMeshLevelOfDetailTest {

    private static final float NEAR_SWITCH = 20.0f;
    private static final float FAR_SWITCH = 50.0f;

    private static MultiMeshRenderer rendererWithTwoLevels() {
        return new MultiMeshRenderer()
                .addLevelOfDetailPath("res://foliage_level_one.epymesh", NEAR_SWITCH)
                .addLevelOfDetailPath("res://foliage_level_two.epymesh", FAR_SWITCH);
    }

    private static int levelAt(MultiMeshRenderer renderer, float distance) {
        renderer.meshForDistance(distance);
        return renderer.activeLevelOfDetail();
    }

    @Test
    void eachSwitchDistanceStartsItsOwnLevel() {
        MultiMeshRenderer renderer = rendererWithTwoLevels();

        assertEquals(2, renderer.levelOfDetailCount());
        assertEquals(0, levelAt(renderer, 0.0f));
        assertEquals(0, levelAt(renderer, 19.9f));
        assertEquals(1, levelAt(renderer, NEAR_SWITCH));
        assertEquals(1, levelAt(renderer, 49.9f));
        assertEquals(2, levelAt(renderer, FAR_SWITCH));
        assertEquals(2, levelAt(renderer, 1000.0f));
    }

    @Test
    void aLevelIsHeldUntilTheDistanceFallsBelowTheHysteresisBand() {
        MultiMeshRenderer renderer = rendererWithTwoLevels();

        assertEquals(2, levelAt(renderer, 60.0f));
        assertEquals(2, levelAt(renderer, 47.0f));
        assertEquals(1, levelAt(renderer, 45.0f));
        assertEquals(1, levelAt(renderer, 19.0f));
        assertEquals(0, levelAt(renderer, 18.0f));
    }
}
