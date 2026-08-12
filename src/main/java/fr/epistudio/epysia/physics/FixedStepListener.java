package fr.epistudio.epysia.physics;

import fr.epistudio.epysia.scene.Scene;

@FunctionalInterface
public interface FixedStepListener {
    void onFixedStep(Scene scene, float fixedStepSeconds);
}
