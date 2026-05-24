package fr.epistudio.epysia.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

public final class CountSystem implements GameSystem {

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(CountComponent.class).ifPresent(CountComponent::increment);
        }
    }
}
