package fr.epistudio.epysia.assets;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetUriTest {

    @Test
    void windowsSeparatorsSurviveTheRoundTrip() {
        AssetUri written = AssetUri.project("sprites\\hero\\idle.png");
        assertEquals("res://sprites/hero/idle.png", written.toString());
        assertEquals(Optional.of(written), AssetUri.parse(written.toString()));
    }

    @Test
    void relativeSegmentsCollapseAgainstTheOrigin() {
        AssetUri atlas = AssetUri.project("atlases/hero.epyatlas");
        assertEquals("res://textures/hero.png", atlas.resolveRelative("../textures/hero.png").toString());
    }

    @Test
    void aSchemedRelativePathReplacesTheOrigin() {
        AssetUri atlas = AssetUri.project("atlases/hero.epyatlas");
        assertEquals("engine://shaders/pbr.glsl", atlas.resolveRelative("engine://shaders/pbr.glsl").toString());
    }

    @Test
    void anEmptyUriRendersAsAnEmptyString() {
        assertTrue(AssetUri.empty().isEmpty());
        assertEquals("", AssetUri.empty().toString());
    }
}
