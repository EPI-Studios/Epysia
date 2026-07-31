package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.components.SpriteFlipbook;
import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpriteRendererRoundTripTest {

    private static final int SORTING_LAYER = 1;

    @Test
    void keepsSortingLayerThroughSerialization() {
        SpriteRenderer restored = roundTrip(false);

        assertEquals(SORTING_LAYER, restored.sortingLayer(),
                "sorting layer must survive a scene round trip");
    }

    @Test
    void keepsSortingLayerWhenAFlipbookSitsOnTheSameObject() {
        SpriteRenderer restored = roundTrip(true);

        assertEquals(SORTING_LAYER, restored.sortingLayer(),
                "a sibling flipbook must not drop the sprite sorting layer");
    }

    private static SpriteRenderer roundTrip(boolean withFlipbook) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene source = new Scene("source");
        source.addGameObject(buildSprite(withFlipbook));
        source.advanceTick();
        String text = serializer.serialize(source, gameObject -> true);
        Scene target = new Scene("target");
        serializer.deserialize(target, text, null);
        target.advanceTick();
        return target.gameObjects().get(0).getComponentOrNull(SpriteRenderer.class);
    }

    private static GameObject buildSprite(boolean withFlipbook) {
        GameObject object = new GameObject("sprite");
        object.addComponent(new Transform2D());
        if (withFlipbook) {
            object.addComponent(new SpriteFlipbook().setAtlasPath("atlas.epyatlas"));
        }
        SpriteRenderer sprite = object.getComponentOrNull(SpriteRenderer.class);
        if (sprite == null) {
            sprite = object.addComponent(new SpriteRenderer());
        }
        sprite.setTexturePath("sheet.png").setPixelsPerUnit(8.0f).setSortingLayer(SORTING_LAYER);
        return object;
    }
}
