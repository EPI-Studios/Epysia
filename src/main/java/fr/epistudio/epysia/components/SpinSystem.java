package fr.epistudio.epysia.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;

public final class SpinSystem implements GameSystem {

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(SpinComponent.class).ifPresent(spin ->
                    gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                            applySpin(transform, spin, deltaTimeSeconds)
                    )
            );
        }
    }

    private void applySpin(Transform3D transform, SpinComponent spin, float deltaTimeSeconds) {
        transform.rotateAxisAngle(
                spin.axis().x,
                spin.axis().y,
                spin.axis().z,
                spin.radiansPerSecond() * deltaTimeSeconds
        );
    }
}
