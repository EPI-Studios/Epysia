package fr.epistudio.epysia.assets.epyatlas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteAtlasJsonCodecTest {

    private final SpriteAtlasJsonCodec codec = new SpriteAtlasJsonCodec();

    @Test
    void gridAtlasWithoutAnimationsRoundTripsWithoutAnimationsSection() {
        SpriteAtlas original = SpriteAtlas.gridAtlas("sheet.png",
                new SpriteAtlasGrid(16, 16, 4, 2), List.of());
        String json = codec.write(original);
        assertFalse(json.contains("animations"));
        SpriteAtlas decoded = codec.read(json);
        assertTrue(decoded.animations().isEmpty());
        assertEquals(json, codec.write(decoded));
    }

    @Test
    void animationsSectionRoundTripsByteStable() {
        SpriteAtlas original = SpriteAtlas.gridAtlas("sheet.png",
                new SpriteAtlasGrid(16, 16, 4, 2), List.of(),
                List.of(new SpriteAnimation("walk", 10.0f, true, List.of("0", "1", "2", "1")),
                        new SpriteAnimation("idle", 4.0f, false, List.of("3"))));
        String json = codec.write(original);
        SpriteAtlas decoded = codec.read(json);
        assertEquals(json, codec.write(decoded));
        assertAnimationsDecoded(decoded);
    }

    private static void assertAnimationsDecoded(SpriteAtlas decoded) {
        assertEquals(List.of("walk", "idle"), decoded.animationNames());
        Optional<SpriteAnimation> walk = decoded.animation("walk");
        assertTrue(walk.isPresent());
        assertEquals(10.0f, walk.get().framesPerSecond());
        assertTrue(walk.get().loop());
        assertEquals(List.of("0", "1", "2", "1"), walk.get().frames());
        Optional<SpriteAnimation> idle = decoded.animation("idle");
        assertTrue(idle.isPresent());
        assertFalse(idle.get().loop());
    }

    @Test
    void atlasJsonWithoutAnimationsKeyStillReads() {
        SpriteAtlas decoded = codec.read("""
                {"texture": "sheet.png", "grid": {"cellWidth": 8, "cellHeight": 8, "columns": 2, "rows": 2}, "regions": []}
                """);
        assertTrue(decoded.animations().isEmpty());
        assertEquals(4, decoded.regionCount());
    }
}
