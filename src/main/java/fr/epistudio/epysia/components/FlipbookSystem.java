package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

public final class FlipbookSystem implements GameSystem {

    private EngineServices services;

    @Override
    public void initialize(EngineServices services) {
        this.services = services;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (SpriteRenderer sprite : scene.componentsOf(SpriteRenderer.class)) {
            sprite.refreshAtlas(services);
        }
        for (SpriteFlipbook flipbook : scene.componentsOf(SpriteFlipbook.class)) {
            flipbook.advance(deltaTimeSeconds);
        }
    }
}
